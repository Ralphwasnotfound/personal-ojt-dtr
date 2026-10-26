package com.lazarus.personalojtdtr.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lazarus.personalojtdtr.ui.theme.CameraPalette
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme
import com.lazarus.personalojtdtr.ui.ActionLabel
import com.lazarus.personalojtdtr.ui.AppIcon
import com.lazarus.personalojtdtr.ui.AppSymbol
import com.lazarus.personalojtdtr.ui.attendance.LocalPhoto
import java.io.File

private val LiveCameraColors = darkColorScheme(
    primary = CameraPalette.Accent, onPrimary = CameraPalette.Background,
    background = CameraPalette.Background, onBackground = CameraPalette.Content,
    surface = CameraPalette.Surface, onSurface = CameraPalette.Content,
)

/** Shared presentation only: callers retain ownership of drafts, validation and confirmation. */
@Composable internal fun CameraDialog(
    sessionId: String,
    title: String,
    file: File,
    reviewing: Boolean,
    working: Boolean,
    error: String?,
    reviewNote: String,
    confirmLabel: String,
    onCaptureStarted: () -> Boolean,
    onSaved: () -> Unit,
    onFailure: (String) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val appColors = MaterialTheme.colorScheme
    val appDark = LocalOjtDarkTheme.current
    val context = LocalContext.current
    val hostView = LocalView.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    // Never automatically re-prompt after a denial or configuration change.
    var requested by rememberSaveable(sessionId) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var front by rememberSaveable(sessionId) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var flash by rememberSaveable(sessionId) { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var hasFlash by remember { mutableStateOf(false) }
    var canSwitch by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    // Immediate local latch complements the synchronous ViewModel gate before recomposition.
    var requestInFlight by remember { mutableStateOf(false) }
    val saved by rememberUpdatedState(onSaved)
    val failed by rememberUpdatedState(onFailure)
    val locked = working || requestInFlight
    val reviewAlpha by animateFloatAsState(if (reviewing) 1f else 0f, label = "Photo review")
    LaunchedEffect(working, reviewing) { if (!working) requestInFlight = false }
    LaunchedEffect(granted, reviewing) {
        if (!granted && !requested && !reviewing) { requested = true; permission.launch(Manifest.permission.CAMERA) }
    }
    LaunchedEffect(capture, cameraError, granted, reviewing, retry, front, resumed) {
        if (granted && !reviewing && resumed && capture == null && cameraError == null) {
            kotlinx.coroutines.delay(15_000)
            cameraError = "Camera is taking too long to start. Retry or close the camera."
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                resumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = { if (!locked) onCancel() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !locked,
        dismissOnClickOutside = false, decorFitsSystemWindows = false,
    )) {
        val live = !reviewing && granted
        DialogSystemBars(dark = if (live) true else appDark, edgeToEdge = true)
        MaterialTheme(colorScheme = if (live) LiveCameraColors else appColors) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CameraIcon(AppSymbol.CLOSE, "Close camera", !locked, onCancel)
                        Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
                        if (!reviewing && granted) {
                            if (hasFlash) CameraIcon(
                                when (flash) { ImageCapture.FLASH_MODE_ON -> AppSymbol.FLASH_ON; ImageCapture.FLASH_MODE_AUTO -> AppSymbol.FLASH_AUTO; else -> AppSymbol.FLASH_OFF },
                                "Flash ${when (flash) { ImageCapture.FLASH_MODE_ON -> "on"; ImageCapture.FLASH_MODE_AUTO -> "auto"; else -> "off" }}. Change flash mode",
                                !locked && capture != null,
                            ) { flash = when (flash) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON; ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO; else -> ImageCapture.FLASH_MODE_OFF } }
                            if (canSwitch) CameraIcon(AppSymbol.SWITCH_CAMERA, "Switch front or back camera", !locked && capture != null) {
                                capture = null; hasFlash = false; canSwitch = false
                                front = !front; cameraError = null; flash = ImageCapture.FLASH_MODE_OFF
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).background(if (granted || reviewing) CameraPalette.Background else MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        when {
                            reviewing -> key(sessionId, reviewing) {
                                CompositionLocalProvider(LocalContentColor provides CameraPalette.Content) {
                                    LocalPhoto(file, "Captured $title photo", Modifier.fillMaxSize().graphicsLayer { alpha = reviewAlpha }, maxEdge = 1000)
                                }
                            }
                            !granted -> Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                AppIcon(AppSymbol.CAMERA, modifier = Modifier.size(40.dp))
                                Text("Camera permission is needed to take a photo.")
                                // Settings works for both denial and permanent denial; no repeated permission requests.
                                Button(onClick = {
                                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                                        .onFailure { cameraError = "Open Android Settings to allow camera access." }
                                }) { ActionLabel(AppSymbol.APPEARANCE, "Open app settings", compact = true) }
                            }
                            else -> {
                                CameraPreview(front, retry, flash, Modifier.fillMaxSize(),
                                    onReady = { capture = it },
                                    onCapabilities = { flashAvailable, switchAvailable -> hasFlash = flashAvailable; canSwitch = switchAvailable },
                                    onError = { cameraError = it })
                                if (capture == null && cameraError == null) CircularProgressIndicator(Modifier.size(32.dp), color = CameraPalette.Content)
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val message = error ?: cameraError
                        if (message != null) Surface(color = appColors.errorContainer, contentColor = appColors.onErrorContainer, shape = MaterialTheme.shapes.small) {
                            Text(message, Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                        }
                        if (locked) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                            Text(if (reviewing) "Processing photo…" else "Capturing photo…", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                        }
                        if (reviewing) {
                            Text(reviewNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onRetake, enabled = !locked, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { ActionLabel(AppSymbol.RESTORE, "Retake", compact = true) }
                                Button(onClick = onConfirm, enabled = !locked, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { ActionLabel(AppSymbol.CHECK, confirmLabel, compact = true) }
                            }
                        } else {
                            Shutter(enabled = granted && capture != null && resumed && !locked && cameraError == null) {
                                val camera = capture ?: return@Shutter
                                if (requestInFlight || !onCaptureStarted()) return@Shutter
                                requestInFlight = true
                                try {
                                    camera.targetRotation = hostView.display?.rotation ?: android.view.Surface.ROTATION_0
                                    camera.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(result: ImageCapture.OutputFileResults) { saved(); requestInFlight = false }
                                        override fun onError(exception: ImageCaptureException) { failed(captureErrorMessage(exception)); requestInFlight = false }
                                    })
                                } catch (_: Exception) { failed("Camera unavailable. Retry or close the camera."); requestInFlight = false }
                            }
                            if (cameraError != null && granted) TextButton(onClick = { capture = null; cameraError = null; retry++ }, enabled = !locked) { ActionLabel(AppSymbol.RESTORE, "Retry camera", compact = true) }
                            else Text("Photo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun captureErrorMessage(error: ImageCaptureException): String = when (error.imageCaptureError) {
    ImageCapture.ERROR_FILE_IO -> "Photo could not be saved. Check free storage and retry."
    ImageCapture.ERROR_CAMERA_CLOSED -> "Camera was interrupted. Return to the app and retry."
    else -> "Photo capture failed. Retry or close the camera."
}

@Composable private fun CameraIcon(symbol: AppSymbol, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)) {
        AppIcon(symbol, description, Modifier.size(24.dp))
    }
}

@Composable private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "Shutter press")
    // Compose's duration scale follows the system animator setting.
    OutlinedButton(onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = Modifier.size(84.dp).scale(scale).semantics { contentDescription = "Capture photo" },
        shape = CircleShape, contentPadding = PaddingValues(6.dp), border = androidx.compose.foundation.BorderStroke(3.dp, if (enabled) CameraPalette.ShutterRing else CameraPalette.DisabledRing),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)) {
        Box(Modifier.fillMaxSize().background(if (enabled) CameraPalette.Content else CameraPalette.Disabled, CircleShape))
    }
}
