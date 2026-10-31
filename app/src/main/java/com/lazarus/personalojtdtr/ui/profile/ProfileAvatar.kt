package com.lazarus.personalojtdtr.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.data.photos.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun ProfileAvatar(file: File?, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { file?.let { runCatching { AvatarImages.validate(it); AttendanceImageProcessor.decode(it, 256) }.getOrNull() } }
    }
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), "Profile photo", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop) } ?: placeholder()
        }
    }
}

@Composable fun AvatarPreviewDialog(draft: AvatarDraft, busy: Boolean, onConfirm: (Float, Float, Float) -> Unit, onCancel: () -> Unit) {
    var zoom by remember(draft.id) { mutableFloatStateOf(1f) }
    var x by remember(draft.id) { mutableFloatStateOf(0f) }
    var y by remember(draft.id) { mutableFloatStateOf(0f) }
    // Keyed result prevents confirming a crop whose preview has not finished rendering.
    val preview by produceState<Pair<Triple<Float, Float, Float>, android.graphics.Bitmap?>?>(null, draft.id, zoom, x, y) {
        val key = Triple(zoom, x, y)
        value = key to withContext(Dispatchers.Default) { runCatching { AvatarImages.crop(draft.file, key.first, key.second, key.third) }.getOrNull() }
    }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("Preview profile photo") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            preview?.second?.let { Image(it.asImageBitmap(), "Profile photo preview", Modifier.size(180.dp).clip(CircleShape), contentScale = ContentScale.Crop) }
                ?: Text("Preparing photo…")
            Text("Position your photo inside the circle. It is saved only when you confirm.", style = MaterialTheme.typography.bodySmall)
            Text("Zoom"); Slider(zoom, { zoom = it }, valueRange = 1f..3f, enabled = !busy)
            Text("Horizontal position"); Slider(x, { x = it }, valueRange = -1f..1f, enabled = !busy)
            Text("Vertical position"); Slider(y, { y = it }, valueRange = -1f..1f, enabled = !busy)
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(zoom, x, y) }, enabled = !busy && preview?.first == Triple(zoom, x, y) && preview?.second != null) { Text(if (busy) "Saving…" else "Confirm Photo") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } })
}
