package com.lazarus.personalojtdtr.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.attendance.CameraPreview
import com.lazarus.personalojtdtr.ui.attendance.LocalPhoto

@Composable fun ActivityCameraScreen(state: ActivityCamera, model: ActivityViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var front by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = { if (!state.working) model.cancelCamera() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !state.working, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Activity photo", style = MaterialTheme.typography.headlineSmall)
                Text("No attendance is recorded. Your journal text stays in the editor.")
                when {
                    state.photo != null -> {
                        LocalPhoto(state.file, "Captured activity photo", Modifier.fillMaxWidth().height(340.dp))
                        Button(onClick = model::usePhoto, enabled = !state.working) { Text("Use photo") }
                        OutlinedButton(onClick = model::retake, enabled = !state.working) { Text("Retake") }
                    }
                    !granted -> {
                        Text("Camera permission is needed to capture a photo. You can also select a photo in the editor.")
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                        TextButton(onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }.onFailure { error = "Open Android Settings to grant camera permission." } }) { Text("App permission settings") }
                    }
                    else -> {
                        CameraPreview(front, retry, { capture = it }, { error = "Camera unavailable. Retry, switch camera or cancel." })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { front = !front; error = null }, enabled = !state.working) { Text("Switch camera") }
                            TextButton(onClick = { retry++; error = null }, enabled = !state.working) { Text("Retry") }
                        }
                        Button(enabled = capture != null && !state.working, onClick = {
                            val camera = capture ?: return@Button
                            if (model.captureStarted()) try {
                                camera.takePicture(ImageCapture.OutputFileOptions.Builder(state.file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) { model.captured(state.id) }
                                    override fun onError(exception: ImageCaptureException) { model.captureFailed(state.id) }
                                })
                            } catch (_: Exception) { model.captureFailed(state.id) }
                        }) { AppIcon(AppSymbol.CAMERA); Spacer(Modifier.width(8.dp)); Text("Capture photo") }
                    }
                }
                (state.error ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = model::cancelCamera, enabled = !state.working) { Text("Cancel camera") }
            }
        }
    }
}
