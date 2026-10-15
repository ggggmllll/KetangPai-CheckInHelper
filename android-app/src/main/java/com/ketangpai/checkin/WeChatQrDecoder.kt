package com.ketangpai.checkin

import android.content.Context
import org.opencv.OpenCV
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.wechat_qrcode.WeChatQRCode
import java.io.File

/** Confined to one worker thread, including initialization and release. */
internal class WeChatQrDecoder(context: Context) : AutoCloseable {
    private val detector: WeChatQRCode
    private val gray: Mat
    private val rotated: Mat
    private var frame = 0

    init {
        detector = sharedDetector(context)
        gray = Mat()
        rotated = Mat()
    }

    fun decode(luminance: ByteArray, width: Int, height: Int, rotation: Int): List<String> {
        gray.create(height, width, CvType.CV_8UC1)
        gray.put(0, 0, luminance)
        val image = when (rotation) {
            90 -> { Core.rotate(gray, rotated, Core.ROTATE_90_CLOCKWISE); rotated }
            180 -> { Core.rotate(gray, rotated, Core.ROTATE_180); rotated }
            270 -> { Core.rotate(gray, rotated, Core.ROTATE_90_COUNTERCLOCKWISE); rotated }
            else -> gray
        }
        // The detector scales large images down internally. Alternating with a central
        // crop gives small central codes more detector pixels without upscaling the frame.
        // Full-frame passes still find codes outside the guide; never queue old frames.
        val centerPass = frame++ % 2 == 1
        if (!centerPass) return synchronized(detector) { detector.detectAndDecode(image) }
        val side = (minOf(image.cols(), image.rows()) * 0.8).toInt().coerceAtLeast(1)
        val center = image.submat(Rect((image.cols() - side) / 2, (image.rows() - side) / 2, side, side))
        return try { synchronized(detector) { detector.detectAndDecode(center) } } finally { center.release() }
    }

    override fun close() {
        gray.release()
        rotated.release()
        // The detector/models stay warm for the process lifetime, like the reference app.
    }

    companion object {
        private var instance: WeChatQRCode? = null

        @Synchronized
        private fun sharedDetector(context: Context): WeChatQRCode {
            instance?.let { return it }
            check(OpenCV.initOpenCV()) { "OpenCV 本地库加载失败" }
            val directory = File(context.filesDir, "qrcode-models-v1")
            check(directory.isDirectory || directory.mkdirs()) { "无法创建扫码模型目录" }
            val paths = listOf("detect.prototxt", "detect.caffemodel", "sr.prototxt", "sr.caffemodel").map { name ->
                val file = File(directory, name)
                context.assets.open("models/$name").use { source ->
                    // A failed/partial previous copy must not be reused.
                    if (!file.isFile || file.length() != source.available().toLong()) file.outputStream().use { source.copyTo(it) }
                }
                file.absolutePath
            }
            return WeChatQRCode(paths[0], paths[1], paths[2], paths[3]).also { instance = it }
        }
    }
}
