package com.lazarus.personalojtdtr.ui.activity

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.data.report.JournalExportService
import com.lazarus.personalojtdtr.ui.report.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

@Composable fun ActivityListRow(activity: DailyActivity, photos: ActivityPhotoStore, onClick: () -> Unit, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), color = colors.surface,
        border = BorderStroke(0.5.dp, colors.outlineVariant), shadowElevation = 0.5.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ActivityRules.dateLabel(activity.date) + activity.time?.let { " · ${ActivityRules.timeLabel(it)}" }.orEmpty(),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                if (activity.photoPaths.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(50), color = colors.tertiaryContainer) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            AppIcon(AppSymbol.PHOTO, modifier = Modifier.size(15.dp))
                            Text("${activity.photoPaths.size} ${if (activity.photoPaths.size == 1) "photo" else "photos"}", style = MaterialTheme.typography.labelMedium,
                                color = colors.onTertiaryContainer)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(activity.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(activity.description, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                AppIcon(AppSymbol.RIGHT, modifier = Modifier.size(18.dp))
            }
            if (activity.photoPaths.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    activity.photoPaths.take(3).forEachIndexed { index, path ->
                        val file = remember(path) { runCatching { photos.thumbnailFor(path) }.getOrNull() }
                        LocalPhoto(file, "Photo ${index + 1} for ${activity.title}", Modifier.size(40.dp)
                            .clip(RoundedCornerShape(8.dp)), maxEdge = 240)
                    }
                    if (activity.photoPaths.size > 3) Text("+${activity.photoPaths.size - 3}",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun ActivitiesScreen(activities: List<DailyActivity>, busy: Boolean, model: ActivityViewModel) {
    var exporting by rememberSaveable { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as OjtApplication
    val exportModel: JournalExportViewModel = viewModel(factory = JournalExportViewModel.factory(app.repository, JournalExportService(app, app.journal.photos)))
    if (exporting) JournalExportDialog(exportModel) { exporting = false }
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
    var addActionHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val actionInset = with(density) { addActionHeight.toDp() } + AppSpacing.Page
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = AppSpacing.Page, end = AppSpacing.Page,
            top = AppSpacing.Page, bottom = maxOf(104.dp, actionInset)), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Activities", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${activities.size} ${if (activities.size == 1) "entry" else "entries"}",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { exporting = true; exportModel.preview() }, enabled = !busy && !journalBusy,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            ActionLabel(AppSymbol.EXPORT, "Export Journal", compact = true)
                        }
                    }
                }
            }
            if (activities.isEmpty()) item {
                EmptyState("No activities yet", "Add an entry to start your journal. You can attach up to five photos.")
            }
            activities.groupBy { it.date }.forEach { (date, rows) ->
                item(key = "date-$date") { Column(Modifier.padding(top = AppSpacing.Section, bottom = AppSpacing.Related)) { SectionHeading(ActivityRules.dateLabel(date)) } }
                rows.forEach { activity -> item(key = "activity-${activity.id}") { ActivityListRow(activity, model.photos, { model.detail(activity.id) }) } }
            }
        }
        Button(onClick = { model.edit() }, enabled = !busy && !journalBusy,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.align(Alignment.BottomEnd).onSizeChanged { addActionHeight = it.height }.padding(AppSpacing.Page)
                .shadow(if (busy || journalBusy) 0.dp else 3.dp, MaterialTheme.shapes.extraLarge).heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
            ActionLabel(AppSymbol.ADD, "Add activity")
        }
    }
    if (selected != null && editor == null) Dialog(onDismissRequest = { model.detail(null) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogSystemBars(edgeToEdge = true)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(AppSpacing.Page), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIconButton(AppSymbol.BACK, "Back to activities", onClick = { model.detail(null) })
                    Text("Activity details", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    ActionIconButton(AppSymbol.EDIT, "Edit activity", onClick = { model.edit(selected) }, enabled = !journalBusy)
                    ActionIconButton(AppSymbol.DELETE, "Delete activity", onClick = { deleteRequest = selected }, enabled = !journalBusy, destructive = true)
                } }
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selected.title, style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { heading() })
                            Text(ActivityRules.dateLabel(selected.date) + selected.time?.let { " · ${ActivityRules.timeLabel(it)} PH" }.orEmpty(),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { Text(selected.description, style = MaterialTheme.typography.bodyLarge) }
                item { Text("Photos · ${selected.photoPaths.size}", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }) }
                if (selected.photoPaths.isEmpty()) item { Text("No photos attached. Photos are optional.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                selected.photoPaths.forEachIndexed { index, path -> item(key = "photo-$index-$path") {
                    val file = remember(path) { runCatching { model.photos.thumbnailFor(path) }.getOrNull() }
                    Box(Modifier.fillMaxWidth().clickable { viewer = selected.photoPaths to index }) { LocalPhoto(file, "Open activity photo ${index + 1}", Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium), maxEdge = 640) }
                    QuietButton(onClick = { viewer = selected.photoPaths to index }) { ActionLabel(AppSymbol.PHOTO, "View photo ${index + 1}", compact = true) }
                } }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Share", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(onClick = { share(selected, emptyList()) }) { ActionLabel(AppSymbol.SHARE, "Description") }
                            if (selected.photoPaths.isNotEmpty()) SecondaryButton(onClick = { share(selected, selected.photoPaths) }) {
                                ActionLabel(AppSymbol.PHOTO, "Photos + text", compact = true)
                            }
                        }
                    }
                }
                item { Text("Created ${selected.createdAt.manilaLabel()}\nUpdated ${selected.updatedAt.manilaLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (journalBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
    }
    editor?.let { ActivityEditorScreen(it, journalBusy, model) }
    camera?.let { ActivityCameraScreen(it, model) }
    viewer?.let { (paths, index) -> ActivityPhotoViewer(paths, index, selected?.title ?: "Activity photos", model.photos, { viewer = null }, selected?.let { activity -> { path, text -> share(activity, listOf(path), text) } }) }
    deleteRequest?.let { activity -> AlertDialog(onDismissRequest = { deleteRequest = null }, icon = { AppIcon(AppSymbol.DELETE) }, title = { DialogSystemBars(); Text("Delete activity?") },
        text = { Text("Delete “${activity.title}” and its photo references? Other activities and attendance are preserved. This cannot be undone without a backup.") },
        confirmButton = { DestructiveButton(onClick = { model.delete(activity); deleteRequest = null }, enabled = !journalBusy, text = "Delete") },
        dismissButton = { QuietButton(onClick = { deleteRequest = null }) { ActionLabel(AppSymbol.CLOSE, "Cancel", compact = true) } }) }
    operationError?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { DialogSystemBars(); Text("Activity not deleted") }, text = { Text(message) }, confirmButton = { QuietButton(onClick = model::dismissError) { Text("OK") } }) }
    shareError?.let { message -> AlertDialog(onDismissRequest = { shareError = null }, title = { DialogSystemBars(); Text("Unable to share") }, text = { Text(message) }, confirmButton = { QuietButton(onClick = { shareError = null }) { Text("OK") } }) }
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
    Dialog(onDismissRequest = { if (!busy) discard = true }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        DialogSystemBars(edgeToEdge = true)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionIconButton(AppSymbol.CLOSE, "Cancel editing", onClick = { discard = true }, enabled = !busy)
                    Text(if (editor.expectedRevision == null) "New activity" else "Edit activity", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = model::save, enabled = !busy && valid) { ActionLabel(AppSymbol.SAVE, if (busy) "Working…" else "Save", compact = true) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AppSpacing.Page), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(editor.title, { model.fields(title = it.take(121)) }, label = { Text("Activity title *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy, isError = editor.title.trim().length > 120, supportingText = { Text("${editor.title.trim().length}/120") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
                    TextField(editor.description, { model.fields(description = it.take(10001)) }, label = { Text("What did you work on or learn? *") }, minLines = 5, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy, isError = editor.description.trim().length > 10000, supportingText = { Text("Detailed description • ${editor.description.trim().length}/10000") }, colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
                    TextField(editor.date, { model.fields(date = it) }, label = { Text("Activity date * (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                        isError = date == null || date.year !in 1800..2400, supportingText = { Text("Asia/Manila • past dates allowed • 1800–2400") }, trailingIcon = { ActionIconButton(AppSymbol.CALENDAR, "Choose activity date", onClick = { datePicker = true }, enabled = !busy) }, colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
                    TextField(editor.time, { model.fields(time = it) }, label = { Text("Time (optional, HH:mm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                        isError = editor.time.isNotBlank() && time == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done), trailingIcon = { ActionIconButton(AppSymbol.ATTENDANCE, "Choose optional activity time", onClick = { timePicker = true }, enabled = !busy) }, colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
                    if (editor.time.isNotBlank()) QuietButton(onClick = { model.fields(time = "") }, enabled = !busy) { ActionLabel(AppSymbol.CLOSE, "Clear optional time", compact = true) }
                    HorizontalDivider()
                    SectionHeading("Attachments", AppSymbol.PHOTO)
                    StatusPill("${editor.photos.size} / 5 photos", AppSymbol.PHOTO)
                    Text("Optional JPEG, PNG or WebP, up to 20 MiB each. Journal dates do not have to match capture dates.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Related), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                        SecondaryButton(onClick = model::openCamera, enabled = !busy && editor.photos.size < 5) { ActionLabel(AppSymbol.CAMERA, "Camera") }
                        SecondaryButton(onClick = {
                            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (editor.photos.size == 4) chooseOne.launch(request) else chooseMany.launch(request)
                        }, enabled = !busy && editor.photos.size < 5) { ActionLabel(AppSymbol.PHOTO, "Choose photos") }
                    }
                    editor.photos.forEachIndexed { index, photo ->
                        RefinedSurface {
                            Column {
                                val file = remember(photo.path) { runCatching { model.photos.thumbnailFor(photo.path) }.getOrNull() }
                                Box(Modifier.fillMaxWidth().clickable { viewer = index }) { LocalPhoto(file, "Activity attachment ${index + 1}", Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.medium), maxEdge = 400) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Photo ${index + 1}", Modifier.weight(1f))
                                    ActionIconButton(AppSymbol.LEFT, "Move photo ${index + 1} earlier", onClick = { model.move(index, -1) }, enabled = !busy && index > 0)
                                    ActionIconButton(AppSymbol.RIGHT, "Move photo ${index + 1} later", onClick = { model.move(index, 1) }, enabled = !busy && index < editor.photos.lastIndex)
                                    ActionIconButton(AppSymbol.DELETE, "Remove photo ${index + 1}", onClick = { model.remove(index) }, enabled = !busy, destructive = true)
                                }
                            }
                        }
                    }
                    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    QuietButton(onClick = { discard = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.CLOSE, "Cancel editing", compact = true) }
                }
            }
        }
    }
    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1800..2400)
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = { QuietButton(onClick = { state.selectedDateMillis?.let { model.fields(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; datePicker = false }) { ActionLabel(AppSymbol.CHECK, "Use date", compact = true) } }, dismissButton = { QuietButton(onClick = { datePicker = false }) { ActionLabel(AppSymbol.CLOSE, "Cancel", compact = true) } }) { DialogSystemBars(); DatePicker(state) }
    }
    if (timePicker) {
        val state = rememberTimePickerState(time?.hour ?: 8, time?.minute ?: 0, is24Hour = false)
        AlertDialog(onDismissRequest = { timePicker = false }, title = { DialogSystemBars(); Text("Optional activity time") }, text = { TimeInput(state) }, confirmButton = { QuietButton(onClick = { model.fields(time = LocalTime.of(state.hour, state.minute).toString()); timePicker = false }) { ActionLabel(AppSymbol.CHECK, "Use time", compact = true) } }, dismissButton = { QuietButton(onClick = { timePicker = false }) { ActionLabel(AppSymbol.CLOSE, "Cancel", compact = true) } })
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, icon = { AppIcon(AppSymbol.WARNING) }, title = { DialogSystemBars(); Text("Discard unsaved changes?") }, text = { Text("Your saved activity stays unchanged. New unsaved attachments will be removed.") },
        confirmButton = { DestructiveButton(onClick = { model.cancelEditor(); discard = false }, text = "Discard changes") }, dismissButton = { QuietButton(onClick = { discard = false }) { ActionLabel(AppSymbol.EDIT, "Keep editing", compact = true) } })
    viewer?.let { ActivityPhotoViewer(editor.photos.map { it.path }, it, editor.title.ifBlank { "Activity photos" }, model.photos, { viewer = null }) }
}
