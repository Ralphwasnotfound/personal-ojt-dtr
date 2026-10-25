package com.lazarus.personalojtdtr.ui.activity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lazarus.personalojtdtr.domain.model.DailyActivity
import com.lazarus.personalojtdtr.domain.usecase.ActivityRules
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.attendance.LocalPhoto
import com.lazarus.personalojtdtr.util.*
import kotlinx.coroutines.launch
import java.time.*

@Composable fun ActivityCard(activity: DailyActivity, photos: ActivityPhotoStore, onClick: () -> Unit, compact: Boolean = false) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (activity.photoPaths.isNotEmpty()) {
                val file = remember(activity.photoPaths.first()) { runCatching { photos.thumbnailFor(activity.photoPaths.first()) }.getOrNull() }
                LocalPhoto(file, "First photo for ${activity.title}", Modifier.size(if (compact) 64.dp else 80.dp), maxEdge = 320)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(activity.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ActivityRules.dateLabel(activity.date) + activity.time?.let { " • ${ActivityRules.timeLabel(it)}" }.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (!compact) Text(activity.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("${activity.photoPaths.size} ${if (activity.photoPaths.size == 1) "photo" else "photos"}", style = MaterialTheme.typography.labelSmall)
            }
            AppIcon(AppSymbol.RIGHT, "Open activity")
        }
    }
}

@Composable fun ActivitiesScreen(activities: List<DailyActivity>, busy: Boolean, model: ActivityViewModel) {
    val operationError by model.operationError.collectAsStateWithLifecycle()
    val selectedId by model.selectedId.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val camera by model.camera.collectAsStateWithLifecycle()
    val journalBusy by model.busy.collectAsStateWithLifecycle()
    var deleteRequest by remember { mutableStateOf<DailyActivity?>(null) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = activities.firstOrNull { it.id == selectedId }
    fun share(activity: DailyActivity, paths: List<String>, text: Boolean = true) {
        scope.launch {
            try { ActivitySharing.share(context, activity, model.photos, paths, text) }
            catch (_: Exception) { shareError = "A photo may be missing/unreadable, or no compatible sharing app is available. The activity is still saved." }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Activity journal", style = MaterialTheme.typography.headlineMedium)
            Text("Your work, learning and photo documentation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Button(onClick = { model.edit() }, enabled = !busy && !journalBusy) { AppIcon(AppSymbol.ADD); Spacer(Modifier.width(8.dp)); Text("Add activity") } }
        if (activities.isEmpty()) item { InfoCard("Start your OJT journal") {
            AppIcon(AppSymbol.ACTIVITIES)
            Text("Record what you worked on, what you learned, and up to five optional photos. You can document a past day too.")
        } }
        activities.groupBy { it.date }.forEach { (date, rows) ->
            item(key = "date-$date") { Text(ActivityRules.dateLabel(date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            rows.forEach { activity -> item(key = "activity-${activity.id}") { ActivityCard(activity, model.photos, { model.detail(activity.id) }) } }
        }
    }
    if (selected != null && editor == null) Dialog(onDismissRequest = { model.detail(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { model.detail(null) }) { AppIcon(AppSymbol.BACK, "Back to activities") }
                    Text("Activity details", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { model.edit(selected) }, enabled = !journalBusy) { AppIcon(AppSymbol.EDIT, "Edit activity") }
                    IconButton(onClick = { deleteRequest = selected }, enabled = !journalBusy) { AppIcon(AppSymbol.DELETE, "Delete activity") }
                } }
                item { Text(selected.title, style = MaterialTheme.typography.headlineMedium)
                    Text(ActivityRules.dateLabel(selected.date) + selected.time?.let { " • ${ActivityRules.timeLabel(it)} PH Time" }.orEmpty(), color = MaterialTheme.colorScheme.primary) }
                item { Text(selected.description, style = MaterialTheme.typography.bodyLarge) }
                item { Text("Documentation • ${selected.photoPaths.size} photos", style = MaterialTheme.typography.titleMedium) }
                if (selected.photoPaths.isEmpty()) item { Text("No photos attached. Photos are optional.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                selected.photoPaths.forEachIndexed { index, path -> item(key = "photo-$index-$path") {
                    val file = remember(path) { runCatching { model.photos.thumbnailFor(path) }.getOrNull() }
                    Box(Modifier.fillMaxWidth().clickable { viewer = selected.photoPaths to index }) { LocalPhoto(file, "Open activity photo ${index + 1}", Modifier.fillMaxWidth().height(200.dp), maxEdge = 640) }
                    TextButton(onClick = { viewer = selected.photoPaths to index }) { Text("View photo ${index + 1}") }
                } }
                item { InfoCard("Share documentation") {
                    Text("Choose the app and destination yourself. Some apps may omit accompanying text.")
                    OutlinedButton(onClick = { share(selected, emptyList()) }) { AppIcon(AppSymbol.SHARE); Spacer(Modifier.width(8.dp)); Text("Share title & description") }
                    if (selected.photoPaths.isNotEmpty()) OutlinedButton(onClick = { share(selected, selected.photoPaths) }) { Text("Share photos with text") }
                } }
                item { Text("Created ${selected.createdAt.manilaLabel()}\nUpdated ${selected.updatedAt.manilaLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (journalBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
    }
    editor?.let { ActivityEditorScreen(it, journalBusy, model) }
    camera?.let { ActivityCameraScreen(it, model) }
    viewer?.let { (paths, index) -> ActivityPhotoViewer(paths, index, selected?.title ?: "Activity photos", model.photos, { viewer = null }, selected?.let { activity -> { path, text -> share(activity, listOf(path), text) } }) }
    deleteRequest?.let { activity -> AlertDialog(onDismissRequest = { deleteRequest = null }, title = { Text("Delete activity?") },
        text = { Text("Delete “${activity.title}” and its photo references? Other activities and attendance are preserved. This cannot be undone without a backup.") },
        confirmButton = { TextButton(onClick = { model.delete(activity); deleteRequest = null }, enabled = !journalBusy) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteRequest = null }) { Text("Cancel") } }) }
    operationError?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Activity not deleted") }, text = { Text(message) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } }) }
    shareError?.let { message -> AlertDialog(onDismissRequest = { shareError = null }, title = { Text("Unable to share") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { shareError = null }) { Text("OK") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ActivityEditorScreen(editor: ActivityEditor, busy: Boolean, model: ActivityViewModel) {
    var discard by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    val chooseOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { uri -> model.importPhotos(listOf(uri)) } }
    val chooseMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { model.importPhotos(it) }
    val date = runCatching { LocalDate.parse(editor.date) }.getOrNull()
    val time = if (editor.time.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) runCatching { LocalTime.parse(editor.time) }.getOrNull() else null
    val valid = runCatching { ActivityRules.validate(editor.title, editor.description, requireNotNull(date), editor.photos.map { it.path }); require(editor.time.isBlank() || time != null) }.isSuccess
    Dialog(onDismissRequest = { if (!busy) discard = true }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { discard = true }, enabled = !busy) { AppIcon(AppSymbol.CLOSE, "Cancel editing") }
                    Text(if (editor.expectedRevision == null) "New activity" else "Edit activity", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = model::save, enabled = !busy && valid) { Text(if (busy) "Working…" else "Save") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(editor.title, { model.fields(title = it.take(121)) }, label = { Text("Activity title *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy, isError = editor.title.trim().length > 120, supportingText = { Text("${editor.title.trim().length}/120") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(editor.description, { model.fields(description = it.take(10001)) }, label = { Text("What did you work on or learn? *") }, minLines = 5, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy, isError = editor.description.trim().length > 10000, supportingText = { Text("Detailed description • ${editor.description.trim().length}/10000") })
                    OutlinedTextField(editor.date, { model.fields(date = it) }, label = { Text("Activity date * (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                        isError = date == null || date.year !in 1800..2400, supportingText = { Text("Asia/Manila • past dates allowed • 1800–2400") }, trailingIcon = { TextButton(onClick = { datePicker = true }, enabled = !busy) { Text("Choose") } })
                    OutlinedTextField(editor.time, { model.fields(time = it) }, label = { Text("Time (optional, HH:mm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                        isError = editor.time.isNotBlank() && time == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done), trailingIcon = { TextButton(onClick = { timePicker = true }, enabled = !busy) { Text("Choose") } })
                    if (editor.time.isNotBlank()) TextButton(onClick = { model.fields(time = "") }, enabled = !busy) { Text("Clear optional time") }
                    Text("Photos • ${editor.photos.size}/5", style = MaterialTheme.typography.titleMedium)
                    Text("Optional JPEG, PNG or WebP, up to 20 MiB each. Journal dates do not have to match capture dates.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = model::openCamera, enabled = !busy && editor.photos.size < 5) { AppIcon(AppSymbol.CAMERA); Spacer(Modifier.width(8.dp)); Text("Camera") }
                        OutlinedButton(onClick = {
                            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (editor.photos.size == 4) chooseOne.launch(request) else chooseMany.launch(request)
                        }, enabled = !busy && editor.photos.size < 5) { AppIcon(AppSymbol.PHOTO); Spacer(Modifier.width(8.dp)); Text("Choose photos") }
                    }
                    editor.photos.forEachIndexed { index, photo ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                val file = remember(photo.path) { runCatching { model.photos.thumbnailFor(photo.path) }.getOrNull() }
                                Box(Modifier.fillMaxWidth().clickable { viewer = index }) { LocalPhoto(file, "Activity attachment ${index + 1}", Modifier.fillMaxWidth().height(150.dp), maxEdge = 400) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Photo ${index + 1}", Modifier.weight(1f))
                                    IconButton(onClick = { model.move(index, -1) }, enabled = !busy && index > 0) { AppIcon(AppSymbol.LEFT, "Move photo ${index + 1} earlier") }
                                    IconButton(onClick = { model.move(index, 1) }, enabled = !busy && index < editor.photos.lastIndex) { AppIcon(AppSymbol.RIGHT, "Move photo ${index + 1} later") }
                                    IconButton(onClick = { model.remove(index) }, enabled = !busy) { AppIcon(AppSymbol.DELETE, "Remove photo ${index + 1}") }
                                }
                            }
                        }
                    }
                    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick = model::save, enabled = !busy && valid, modifier = Modifier.fillMaxWidth()) { Text("Save activity") }
                    TextButton(onClick = { discard = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel editing") }
                }
            }
        }
    }
    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1800..2400)
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { model.fields(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; datePicker = false }) { Text("Use date") } }, dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
    if (timePicker) {
        val state = rememberTimePickerState(time?.hour ?: 8, time?.minute ?: 0, is24Hour = false)
        AlertDialog(onDismissRequest = { timePicker = false }, title = { Text("Optional activity time") }, text = { TimeInput(state) }, confirmButton = { TextButton(onClick = { model.fields(time = LocalTime.of(state.hour, state.minute).toString()); timePicker = false }) { Text("Use time") } }, dismissButton = { TextButton(onClick = { timePicker = false }) { Text("Cancel") } })
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard unsaved changes?") }, text = { Text("Your saved activity stays unchanged. New unsaved attachments will be removed.") },
        confirmButton = { TextButton(onClick = { model.cancelEditor(); discard = false }) { Text("Discard changes") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    viewer?.let { ActivityPhotoViewer(editor.photos.map { it.path }, it, editor.title.ifBlank { "Activity photos" }, model.photos, { viewer = null }) }
}
