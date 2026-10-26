package com.lazarus.personalojtdtr.ui.activity

import androidx.compose.runtime.Composable
import com.lazarus.personalojtdtr.ui.camera.CameraDialog

@Composable fun ActivityCameraScreen(state: ActivityCamera, model: ActivityViewModel) {
    CameraDialog(
        sessionId = state.id, title = "Activity photo", file = state.file,
        reviewing = state.photo != null, working = state.working, error = state.error,
        reviewNote = "Use photo adds it to your draft. Save the activity to keep it.",
        confirmLabel = "Use photo", onCaptureStarted = model::captureStarted,
        onSaved = { model.captured(state.id) }, onFailure = { model.captureFailed(state.id, it) },
        onRetake = model::retake, onConfirm = model::usePhoto, onCancel = model::cancelCamera,
    )
}
