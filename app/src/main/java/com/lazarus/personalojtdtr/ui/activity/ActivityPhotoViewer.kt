package com.lazarus.personalojtdtr.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.attendance.LocalPhoto

@Composable fun ActivityPhotoViewer(paths: List<String>, initialIndex: Int, title: String, photos: ActivityPhotoStore,
    onDismiss: () -> Unit, onShare: ((String, Boolean) -> Unit)? = null) {
    if (paths.isEmpty()) return
    var index by remember(paths, initialIndex) { mutableIntStateOf(initialIndex.coerceIn(paths.indices)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { AppIcon(AppSymbol.BACK, "Back to activity") }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text("${index + 1} / ${paths.size}")
                }
                val file = remember(paths[index]) { runCatching { photos.resolve(paths[index]) }.getOrNull() }
                LocalPhoto(file, "Photo ${index + 1} of ${paths.size} for $title", Modifier.fillMaxWidth().weight(1f), maxEdge = 1600)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { index-- }, enabled = index > 0) { AppIcon(AppSymbol.LEFT, "Previous photo") }
                    if (onShare != null) TextButton(onClick = { onShare(paths[index], false) }) { AppIcon(AppSymbol.SHARE); Spacer(Modifier.width(8.dp)); Text("Share photo") }
                    IconButton(onClick = { index++ }, enabled = index < paths.lastIndex) { AppIcon(AppSymbol.RIGHT, "Next photo") }
                }
                if (onShare != null) TextButton(onClick = { onShare(paths[index], true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Share photo with activity text") }
                else Text("Save the activity before sharing its photos.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
