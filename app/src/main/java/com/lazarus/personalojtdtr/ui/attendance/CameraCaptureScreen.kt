package com.lazarus.personalojtdtr.ui.attendance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lazarus.personalojtdtr.ui.CaptureUi

@Composable fun CameraCaptureScreen(
    state: CaptureUi,
    onCaptureStarted: () -> Boolean,
    onSaved: (String) -> Unit,
    onFailure: (String, String) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var front by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (!permission) request.launch(Manifest.permission.CAMERA) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = { if (!state.working) onCancel() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !state.working, dismissOnClickOutside = false,
    )) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.draft.type.label, style = MaterialTheme.typography.headlineSmall)
                Text("${state.draft.date} • New camera photo required")
                when {
                    state.draft.capturedAt != null -> {
                        LocalPhoto(state.draft.file, "Captured attendance photo", Modifier.fillMaxWidth().height(360.dp))
                        Text("Confirm records the current device time in Asia/Manila. This timestamp is not independently verified.")
                        Button(onClick = onConfirm, enabled = !state.working) { Text(if (state.working) "Saving…" else "Confirm attendance") }
                        OutlinedButton(onClick = onRetake, enabled = !state.working) { Text("Retake") }
                    }
                    !permission -> {
                        Text("Camera permission is required. No attendance has been recorded.")
                        Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("Grant camera permission") }
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            }.onFailure { cameraError = "App settings could not be opened. Use Android Settings to grant camera permission." }
                        }) { Text("Open app permission settings") }
                    }
                    else -> {
                        CameraPreview(front, attempt, { capture = it }, { cameraError = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { front = !front; cameraError = null }, enabled = !state.working) { Text("Switch camera") }
                            TextButton(onClick = { attempt++; cameraError = null }, enabled = !state.working) { Text("Retry camera") }
                        }
                        Button(enabled = capture != null && !state.working, onClick = {
                            val camera = capture ?: return@Button
                            if (onCaptureStarted()) {
                                try {
                                    camera.takePicture(ImageCapture.OutputFileOptions.Builder(state.draft.file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(result: ImageCapture.OutputFileResults) { onSaved(state.draft.id) }
                                        override fun onError(error: ImageCaptureException) { onFailure(state.draft.id, error.message ?: "Camera capture failed") }
                                    })
                                } catch (e: Exception) { onFailure(state.draft.id, e.message ?: "Camera unavailable") }
                            }
                        }) { Text(if (state.working) "Capturing…" else "Capture new photo") }
                    }
                }
                cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = onCancel, enabled = !state.working) { Text("Cancel") }
            }
        }
    }
}

@Composable internal fun CameraPreview(front: Boolean, attempt: Int, onReady: (ImageCapture?) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val ready by rememberUpdatedState(onReady)
    val error by rememberUpdatedState(onError)
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(360.dp))
    DisposableEffect(lifecycle, view, front, attempt) {
        var active = true
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(90).setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()).build()
        val future = try { ProcessCameraProvider.getInstance(context) }
            catch (e: Exception) { error("Camera initialization failed: ${e.message}"); null }
        future?.addListener({
            if (active) {
                try {
                    val cameras = future.get().also { provider = it }
                    val preferred = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    val fallback = if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    val selector = when {
                        cameras.hasCamera(preferred) -> preferred
                        cameras.hasCamera(fallback) -> fallback
                        else -> kotlin.error("No usable camera is available on this device.")
                    }
                    capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                    cameras.bindToLifecycle(lifecycle, selector, preview, capture)
                    ready(capture)
                } catch (e: Exception) {
                    ready(null)
                    error("Camera unavailable: ${e.message}. Retry or cancel.")
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            ready(null)
            provider?.unbind(preview, capture)
        }
    }
}
