package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.domain.model.AttendanceEvent
import com.lazarus.personalojtdtr.ui.ActionLabel
import com.lazarus.personalojtdtr.ui.AppIcon
import com.lazarus.personalojtdtr.ui.AppSymbol
import com.lazarus.personalojtdtr.ui.QuietButton
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
    AlertDialog(onDismissRequest = onDismiss, title = { DialogSystemBars(); Text(event.type.label) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !original, onClick = { original = false }, label = { Text("Timestamped") }, leadingIcon = { AppIcon(AppSymbol.ATTENDANCE, modifier = Modifier.size(18.dp)) })
                FilterChip(selected = original, onClick = { original = true }, label = { Text("Original") }, leadingIcon = { AppIcon(AppSymbol.PHOTO, modifier = Modifier.size(18.dp)) })
            }
            val path = if (original) event.originalPhotoPath else event.timestampedPhotoPath
            val file = remember(path) { runCatching { photos.resolve(path) }.getOrNull() }
            LocalPhoto(file, event.type.label, Modifier.fillMaxWidth().aspectRatio(1f))
            Text("Timestamp from device clock • Asia/Manila")
        }
    }, confirmButton = { QuietButton(onClick = onShare) { ActionLabel(AppSymbol.SHARE, "Share timestamped photo", compact = true) } },
        dismissButton = { QuietButton(onClick = onDismiss) { ActionLabel(AppSymbol.CLOSE, "Close", compact = true) } })
}
