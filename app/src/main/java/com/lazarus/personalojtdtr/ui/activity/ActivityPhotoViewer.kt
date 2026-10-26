package com.lazarus.personalojtdtr.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogSystemBars(edgeToEdge = true)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIconButton(AppSymbol.BACK, "Back to activity", onClick = onDismiss)
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text("${index + 1} / ${paths.size}")
                }
                val file = remember(paths[index]) { runCatching { photos.resolve(paths[index]) }.getOrNull() }
                LocalPhoto(file, "Photo ${index + 1} of ${paths.size} for $title", Modifier.fillMaxWidth().weight(1f), maxEdge = 1600)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ActionIconButton(AppSymbol.LEFT, "Previous photo", onClick = { index-- }, enabled = index > 0)
                    if (onShare != null) QuietButton(onClick = { onShare(paths[index], false) }, modifier = Modifier.weight(1f)) { ActionLabel(AppSymbol.SHARE, "Share photo") }
                    ActionIconButton(AppSymbol.RIGHT, "Next photo", onClick = { index++ }, enabled = index < paths.lastIndex)
                }
                if (onShare != null) QuietButton(onClick = { onShare(paths[index], true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { ActionLabel(AppSymbol.SHARE, "Share photo with activity text", compact = true) }
                else Text("Save the activity before sharing its photos.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
