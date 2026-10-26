package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.runtime.Composable
import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import com.lazarus.personalojtdtr.ui.CaptureUi
import com.lazarus.personalojtdtr.ui.camera.CameraDialog
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun CameraCaptureScreen(
    state: CaptureUi,
    onCaptureStarted: () -> Boolean,
    onSaved: (String) -> Unit,
    onFailure: (String, String) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val captureTime = state.draft.capturedAt?.let {
        DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH).withZone(AttendanceCalculations.manila).format(it)
    }
    CameraDialog(
        sessionId = state.draft.id, title = state.draft.type.label, file = state.draft.file,
        reviewing = state.draft.capturedAt != null, working = state.working, error = state.error,
        reviewNote = "Captured $captureTime • Asia/Manila. Confirm records the current device time. Device time is not independently verified.",
        confirmLabel = "Confirm attendance", onCaptureStarted = onCaptureStarted,
        onSaved = { onSaved(state.draft.id) }, onFailure = { onFailure(state.draft.id, it) },
        onRetake = onRetake, onConfirm = onConfirm, onCancel = onCancel,
    )
}
