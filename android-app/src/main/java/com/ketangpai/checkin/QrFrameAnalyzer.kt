package com.ketangpai.checkin

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class QrScanStats(
    val width: Int = 0,
    val height: Int = 0,
    val frames: Int = 0,
    val mlFrames: Int = 0,
    val wechatFrames: Int = 0,
    val mlMillis: Long = 0,
    val wechatMillis: Long = 0,
    val candidates: Int = 0,
    val brightness: Int = 128,
    val wechatState: String = "正在加载模型",
    val mlError: String = "",
) {
    val hint: String get() = when {
        mlError.isNotBlank() -> "部分识别引擎出错，可展开扫码诊断查看"
        brightness < 45 -> "画面较暗，请增加照明或打开补光"
        candidates > 0 -> "已找到二维码但未读出，请靠近或增加缩放，并点击二维码对焦"
        else -> "请对准二维码；较小时靠近或增加缩放，模糊时点击对焦"
    }
}

/** ML Kit and WeChat run independently, with at most one frame in each engine. */
internal class QrFrameAnalyzer(
    context: Context,
    private val mainExecutor: Executor,
    onZoom: (Float) -> Boolean,
    private val onStats: (QrScanStats) -> Unit,
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val delivered = AtomicBoolean(false)
    private val wechatReady = AtomicBoolean(false)
    private val wechatBusy = AtomicBoolean(false)
    private val spareLuminance = AtomicReference<ByteArray?>(null)
    private val worker = Executors.newSingleThreadExecutor()
    private var decoder: WeChatQrDecoder? = null // Only accessed on worker.
    private var stats = QrScanStats() // Only accessed on mainExecutor.
    private var lastStatsAt = 0L
    private var lastLogAt = 0L
    private val started = SystemClock.elapsedRealtime()
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()
            .setZoomSuggestionOptions(ZoomSuggestionOptions.Builder { zoom ->
                !closed.get() && !delivered.get() && onZoom(zoom)
            }.setMaxSupportedZoomRatio(8f).build())
            .build(),
    )

    init {
        val application = context.applicationContext
        worker.execute {
            if (!closed.get()) {
                val start = SystemClock.elapsedRealtime()
                try {
                    decoder = WeChatQrDecoder(application)
                    wechatReady.set(true)
                    update(force = true) { it.copy(wechatState = "已就绪") }
                    Log.i(TAG, "WeChat initialized in ${SystemClock.elapsedRealtime() - start}ms")
                } catch (error: Exception) {
                    wechatFailed(error)
                } catch (error: LinkageError) {
                    wechatFailed(error)
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        if (closed.get() || delivered.get()) { proxy.close(); return }
        val start = SystemClock.elapsedRealtime()
        update { it.copy(width = proxy.width, height = proxy.height, frames = it.frames + 1) }
        // Copy grayscale before ML Kit can finish/close the ImageProxy. The native
        // worker never accesses a camera-owned buffer and never queues stale images.
        if (wechatReady.get() && wechatBusy.compareAndSet(false, true)) {
            try {
                val crop = proxy.cropRect
                val width = crop.width()
                val height = crop.height()
                val rotation = proxy.imageInfo.rotationDegrees
                val luminance = spareLuminance.getAndSet(null)?.takeIf { it.size == width * height } ?: ByteArray(width * height)
                val plane = proxy.planes[0]
                copyQrLuminance(plane.buffer, plane.rowStride, plane.pixelStride, crop.left, crop.top, width, height, luminance)
                worker.execute {
                    try {
                        if (!closed.get() && !delivered.get()) {
                            val nativeStart = SystemClock.elapsedRealtime()
                            val values = decoder!!.decode(luminance, width, height, rotation)
                            val millis = SystemClock.elapsedRealtime() - nativeStart
                            var total = 0L
                            var count = 0
                            for (index in luminance.indices step (luminance.size / 512).coerceAtLeast(1)) {
                                total += luminance[index].toInt() and 255; count++
                            }
                            update { it.copy(wechatMillis = millis, wechatFrames = it.wechatFrames + 1, brightness = (total / count).toInt()) }
                            publish(values, "WeChat", millis)
                        }
                    } catch (error: Exception) {
                        wechatFailed(error)
                    } catch (error: LinkageError) {
                        wechatFailed(error)
                    } finally {
                        spareLuminance.set(luminance)
                        wechatBusy.set(false)
                    }
                }
            } catch (error: Exception) {
                wechatBusy.set(false)
                wechatFailed(error)
            }
        }
        try {
            val image = proxy.image
            if (image == null) { proxy.close(); return }
            scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener(mainExecutor) { codes ->
                    val millis = SystemClock.elapsedRealtime() - start
                    update { it.copy(mlFrames = it.mlFrames + 1, mlMillis = millis, candidates = codes.size, mlError = "") }
                    publish(codes.mapNotNull { it.rawValue }, "MLKit", millis)
                }
                .addOnFailureListener(mainExecutor) { error ->
                    update(force = true) { it.copy(mlError = error.localizedMessage ?: "ML Kit 识别失败") }
                    Log.w(TAG, "MLKit decode failed", error)
                }
                .addOnCompleteListener(DIRECT_EXECUTOR) { proxy.close() }
        } catch (error: Exception) {
            proxy.close()
            update(force = true) { it.copy(mlError = error.localizedMessage ?: "无法分析相机画面") }
            Log.w(TAG, "Unable to analyze camera frame", error)
        }
    }

    private fun publish(values: List<String>, engine: String, millis: Long) {
        // Prefer a check-in URL when several codes are visible. Never log its token.
        val nonBlank = values.filter { it.isNotBlank() }
        val value = nonBlank.firstOrNull { it.contains("courseid=") && it.contains("ticketid=") } ?: nonBlank.firstOrNull() ?: return
        if (!closed.get() && delivered.compareAndSet(false, true)) {
            Log.i(TAG, "Decoded by $engine in ${millis}ms; session=${SystemClock.elapsedRealtime() - started}ms")
            mainExecutor.execute { if (!closed.get()) onResult(value) }
        }
    }

    private fun wechatFailed(error: Throwable) {
        wechatReady.set(false)
        Log.e(TAG, "WeChat unavailable; MLKit remains active", error)
        update(force = true) { it.copy(wechatState = "不可用：${error.localizedMessage ?: error.javaClass.simpleName}") }
    }

    private fun update(force: Boolean = false, change: (QrScanStats) -> QrScanStats) {
        mainExecutor.execute {
            if (!closed.get()) {
                stats = change(stats)
                val now = SystemClock.elapsedRealtime()
                if (force || now - lastStatsAt >= 500) {
                    lastStatsAt = now
                    onStats(stats)
                }
                if (now - lastLogAt >= 2_000) {
                    lastLogAt = now
                    Log.d(TAG, "${stats.width}x${stats.height} frames=${stats.frames} MLKit=${stats.mlMillis}ms/${stats.mlFrames} WeChat=${stats.wechatMillis}ms/${stats.wechatFrames} candidates=${stats.candidates} luma=${stats.brightness} WeChatState=${stats.wechatState}")
                }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            scanner.close()
            // Release Mats on their owning thread after any in-flight native call.
            worker.execute { decoder?.close(); decoder = null; spareLuminance.set(null) }
            worker.shutdown()
        }
    }

    companion object {
        private const val TAG = "CheckInQr"
        private val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
