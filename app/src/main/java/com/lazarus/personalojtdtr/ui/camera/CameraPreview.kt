package com.lazarus.personalojtdtr.ui.camera

import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Bind only when lens, retry or lifecycle owner changes; flash updates never rebind. */
@Composable internal fun CameraPreview(
    front: Boolean,
    attempt: Int,
    flash: Int,
    modifier: Modifier,
    onReady: (ImageCapture?) -> Unit,
    onCapabilities: (Boolean, Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        // Both surfaces use 4:3; fill crops the viewport without stretching the sensor image.
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val ready by rememberUpdatedState(onReady)
    val capabilities by rememberUpdatedState(onCapabilities)
    val error by rememberUpdatedState(onError)
    val requestedFlash by rememberUpdatedState(flash)
    var boundCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(flash, boundCapture, flashAvailable) {
        boundCapture?.flashMode = if (flashAvailable) flash else ImageCapture.FLASH_MODE_OFF
    }
    AndroidView(factory = { view }, modifier = modifier)
    DisposableEffect(lifecycle, view, front, attempt) {
        var active = true
        var provider: ProcessCameraProvider? = null
        var boundCamera: Camera? = null
        val stateObserver = Observer<CameraState> { state ->
            if (active && state.error != null) {
                ready(null)
                error("Camera interrupted or unavailable. Return to the app and retry.")
            }
        }
        ready(null)
        capabilities(false, false)
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(90).setResolutionSelector(resolution).build()
        val future = try { ProcessCameraProvider.getInstance(context) }
            catch (_: Exception) { error("Camera initialization failed. Retry or close the camera."); null }
        future?.addListener({
            if (active) {
                try {
                    val cameras = future.get().also { provider = it }
                    val backAvailable = cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val frontAvailable = cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val selector = when {
                        front && frontAvailable -> CameraSelector.DEFAULT_FRONT_CAMERA
                        backAvailable -> CameraSelector.DEFAULT_BACK_CAMERA
                        frontAvailable -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> kotlin.error("No camera")
                    }
                    capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                    val camera = cameras.bindToLifecycle(lifecycle, selector, preview, capture)
                    boundCamera = camera
                    camera.cameraInfo.cameraState.observe(lifecycle, stateObserver)
                    flashAvailable = camera.cameraInfo.hasFlashUnit()
                    capture.flashMode = if (flashAvailable) requestedFlash else ImageCapture.FLASH_MODE_OFF
                    boundCapture = capture
                    capabilities(flashAvailable, frontAvailable && backAvailable)
                    ready(capture)
                } catch (_: Exception) {
                    provider?.unbind(preview, capture)
                    ready(null)
                    error("Camera unavailable. Close other camera apps, then retry.")
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            ready(null)
            boundCapture = null
            flashAvailable = false
            capabilities(false, false)
            boundCamera?.cameraInfo?.cameraState?.removeObserver(stateObserver)
            provider?.unbind(preview, capture)
        }
    }
}
