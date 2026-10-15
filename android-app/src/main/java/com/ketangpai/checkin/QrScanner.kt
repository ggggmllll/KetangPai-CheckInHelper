package com.ketangpai.checkin

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun QrScanner(onClose: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) request.launch(Manifest.permission.CAMERA) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("扫描签到二维码", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onClose) { Text("关闭") }
                }
                if (allowed) {
                    CameraPreview(onResult, Modifier.fillMaxWidth().weight(1f))
                } else {
                    Text("需要相机权限才能扫描二维码。", Modifier.padding(vertical = 20.dp))
                    OutlinedButton(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("允许相机权限") }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val currentOnResult by rememberUpdatedState(onResult)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(QrScanStats()) }
    var noFrames by remember { mutableStateOf(false) }
    var focusState by remember { mutableStateOf("连续自动对焦") }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(stats.frames) {
        if (stats.frames == 0) { delay(5_000); noFrames = true } else noFrames = false
    }

    fun focusAt(x: Float, y: Float) {
        val active = camera ?: return
        focusState = "正在对焦"
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        val future = active.cameraControl.startFocusAndMetering(action)
        future.addListener({
            if (camera === active) {
                runCatching { future.get() }
                    .onSuccess { focusState = if (it.isFocusSuccessful) "已对焦" else "未锁定焦点，可再点击二维码" }
                    .onFailure { focusState = "连续自动对焦" }
                Log.d("CheckInQr", "Focus: $focusState")
            }
        }, mainExecutor)
    }

    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            AndroidView(
                factory = { view },
                modifier = Modifier.fillMaxSize().pointerInput(camera) {
                    detectTapGestures { point -> focusAt(point.x, point.y) }
                },
            )
            Spacer(Modifier.align(Alignment.Center).size(minOf(maxWidth, maxHeight) * 0.72f)
                .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp)))
            Text(
                when {
                    cameraError.isNotBlank() -> cameraError
                    noFrames -> "相机尚未输出画面，请关闭扫码后重试"
                    stats.frames == 0 -> "正在启动相机…"
                    else -> stats.hint
                },
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.65f)).padding(10.dp),
                color = Color.White, style = MaterialTheme.typography.bodySmall,
            )
        }
        if (maxZoomRatio > 1.05f) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${"%.1f".format(Locale.US, zoomRatio)}×", Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = zoomRatio.coerceIn(1f, maxZoomRatio),
                    onValueChange = { value -> camera?.cameraControl?.setZoomRatio(value) },
                    valueRange = 1f..maxZoomRatio,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (hasFlash) {
                TextButton(onClick = { camera?.cameraControl?.enableTorch(!torchEnabled) }) { Text(if (torchEnabled) "关闭补光" else "打开补光") }
            }
            TextButton(onClick = { focusAt(view.width / 2f, view.height / 2f) }, enabled = camera != null) { Text("重新对焦") }
            TextButton(onClick = { showDiagnostics = !showDiagnostics }) { Text(if (showDiagnostics) "收起诊断" else "扫码诊断") }
        }
        if (showDiagnostics) {
            Text(
                "实际取帧 ${stats.width}×${stats.height} · 已收 ${stats.frames} 帧\n" +
                    "ML Kit ${stats.mlMillis}ms / ${stats.mlFrames} 帧 · 候选码 ${stats.candidates}\n" +
                    "WeChat ${stats.wechatMillis}ms / ${stats.wechatFrames} 帧 · ${stats.wechatState}\n" +
                    "亮度 ${stats.brightness}/255 · $focusState" +
                    if (stats.mlError.isBlank()) "" else "\n${stats.mlError}",
                Modifier.fillMaxWidth().padding(bottom = 6.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    DisposableEffect(view, owner) {
        val executor = Executors.newSingleThreadExecutor()
        val disposed = AtomicBoolean(false)
        val analyzer = QrFrameAnalyzer(context, mainExecutor,
            onZoom = { suggested ->
                val active = camera
                if (active == null || disposed.get()) false else {
                    active.cameraControl.setZoomRatio(suggested.coerceIn(1f, minOf(8f, maxZoomRatio)))
                    true
                }
            },
            onStats = { stats = it }, onResult = { currentOnResult(it) },
        )
        val zoomObserver = Observer<ZoomState> { state ->
            zoomRatio = state.zoomRatio
            maxZoomRatio = state.maxZoomRatio.coerceAtLeast(1f)
        }
        val torchObserver = Observer<Int> { torchEnabled = it == TorchState.ON }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        future.addListener({
            if (!disposed.get()) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    // Resolution selection otherwise prioritizes the default 4:3 ratio
                    // over the requested 1920x1080. Show the actual negotiated size.
                    val resolution = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build()
                    val previewCase = Preview.Builder().setResolutionSelector(resolution).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val analysisCase = ImageAnalysis.Builder().setResolutionSelector(resolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    preview = previewCase
                    analysis = analysisCase
                    analysisCase.setAnalyzer(executor, analyzer)
                    val bound = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, previewCase, analysisCase)
                    camera = bound
                    hasFlash = bound.cameraInfo.hasFlashUnit()
                    bound.cameraInfo.zoomState.observe(owner, zoomObserver)
                    bound.cameraInfo.torchState.observe(owner, torchObserver)
                    Log.i("CheckInQr", "Camera bound; analysis=${analysisCase.resolutionInfo?.resolution}; rotation=${analysisCase.resolutionInfo?.rotationDegrees}; maxZoom=$maxZoomRatio")
                    view.post { if (!disposed.get()) focusAt(view.width / 2f, view.height / 2f) }
                } catch (error: Exception) {
                    cameraError = "相机启动失败：${error.localizedMessage ?: "请检查相机权限"}"
                    Log.e("CheckInQr", "CameraX startup failed", error)
                }
            }
        }, mainExecutor)
        onDispose {
            disposed.set(true)
            camera?.cameraInfo?.zoomState?.removeObserver(zoomObserver)
            camera?.cameraInfo?.torchState?.removeObserver(torchObserver)
            camera = null
            analysis?.clearAnalyzer()
            listOfNotNull(preview, analysis).takeIf { it.isNotEmpty() }?.let { provider?.unbind(*it.toTypedArray()) }
            analyzer.close()
            executor.shutdown()
        }
    }
}
