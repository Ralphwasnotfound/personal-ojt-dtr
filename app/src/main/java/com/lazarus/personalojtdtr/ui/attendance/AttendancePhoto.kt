package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.domain.model.AttendanceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun LocalPhoto(file: File?, description: String, modifier: Modifier = Modifier, maxEdge: Int = 1000) {
    var loaded by remember(file?.path) { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, file?.path, maxEdge) {
        value = withContext(Dispatchers.IO) { runCatching { file?.let { AttendanceImageProcessor.decode(it, maxEdge).asImageBitmap() } }.getOrNull() }
        loaded = true
    }
    when {
        !loaded -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        image == null -> Box(modifier, contentAlignment = Alignment.Center) { Text("Photo unavailable", style = MaterialTheme.typography.bodySmall) }
        else -> Image(requireNotNull(image), description, modifier, contentScale = ContentScale.Fit)
    }
}

@Composable fun EventPhotosDialog(event: AttendanceEvent, photos: LocalAttendancePhotos, onShare: () -> Unit, onDismiss: () -> Unit) {
    var original by remember(event.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(event.type.label) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                TextButton(onClick = { original = false }) { Text("Timestamped") }
                TextButton(onClick = { original = true }) { Text("Original") }
            }
            val path = if (original) event.originalPhotoPath else event.timestampedPhotoPath
            val file = remember(path) { runCatching { photos.resolve(path) }.getOrNull() }
            LocalPhoto(file, event.type.label, Modifier.fillMaxWidth().height(320.dp))
            Text("Timestamp from device clock • Asia/Manila")
        }
    }, confirmButton = { TextButton(onClick = onShare) { Text("Share timestamped photo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
