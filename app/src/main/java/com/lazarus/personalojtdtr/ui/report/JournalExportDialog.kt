package com.lazarus.personalojtdtr.ui.report

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private class CreateJournalDocument : ActivityResultContracts.CreateDocument("application/pdf") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun JournalExportDialog(model: JournalExportViewModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(remember { CreateJournalDocument() }, model::saveResult)
    var picking by remember { mutableStateOf<String?>(null) }
    var months by remember { mutableStateOf(false) }
    val idle = state.busy == null
    picking?.let { field ->
        val date = runCatching { java.time.LocalDate.parse(if (field == "start") state.start else state.end) }.getOrNull()?.takeIf { it.year in 1800..2400 } ?: java.time.LocalDate.now()
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1800..2400)
        DatePickerDialog(onDismissRequest = { picking = null }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    val value = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    if (field == "start") model.period(start = value) else model.period(end = value)
                }; picking = null
            }) { Text("Use date") }
        }, dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }) { DialogSystemBars(); DatePicker(picker) }
    }
    AlertDialog(onDismissRequest = { if (idle) onDismiss() },
        title = { DialogSystemBars(); Text("Export Journal") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Offline PDF · A4 · Philippine time")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = state.custom, onCheckedChange = { model.period(custom = it) }, enabled = idle)
                    Text(if (state.custom) "Custom date range" else "Monthly export")
                }
                if (state.custom) {
                    OutlinedTextField(state.start, { model.period(start = it) }, label = { Text("Start date (YYYY-MM-DD)") }, enabled = idle, singleLine = true, trailingIcon = { ActionIconButton(AppSymbol.CALENDAR, "Choose start date", onClick = { picking = "start" }, enabled = idle) })
                    OutlinedTextField(state.end, { model.period(end = it) }, label = { Text("End date (YYYY-MM-DD)") }, enabled = idle, singleLine = true, trailingIcon = { ActionIconButton(AppSymbol.CALENDAR, "Choose end date", onClick = { picking = "end" }, enabled = idle) })
                } else {
                Box {
                    OutlinedButton(onClick = { months = true }, enabled = idle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        ActionLabel(AppSymbol.CALENDAR, Month.of(state.month).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    }
                    DropdownMenu(expanded = months, onDismissRequest = { months = false }) {
                        Month.entries.forEach { month -> DropdownMenuItem(text = { Text(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)) },
                            onClick = { model.period(month = month.value); months = false }) }
                    }
                }
                OutlinedTextField(value = state.year, onValueChange = { model.period(year = it) }, label = { Text("Year") }, enabled = idle,
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = model::preview, enabled = idle, modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.HISTORY, "Preview period") }
                state.report?.let { report ->
                    Text(report.period.label, style = MaterialTheme.typography.titleMedium)
                    Text("Student name: ${report.student}")
                    if (report.student == "Not provided") Text("Student name is missing. Add it in Profile, then preview again.")
                    Text("${report.activities.size} activities · ${report.photoCount} attached photographs")
                    if (report.warnings.isNotEmpty()) {
                        Text("${report.warnings.size} missing or unreadable photographs", color = MaterialTheme.colorScheme.error)
                        report.warnings.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (report.warnings.size > 5) Text("Additional unavailable photos will be labeled in the PDF.")
                    }
                    if (report.activities.isEmpty()) Text("No saved activities in this period. Choose another period or add an activity to your journal.")
                    else {
                        Text("Saved-data preview. Preview again to include newer records. Unavailable photos receive placeholders.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = model::generate, enabled = idle, modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.EXPORT, "Generate PDF") }
                    }
                }
                state.busy?.let {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(it)
                    TextButton(onClick = model::cancel, enabled = it != "Choose a save location…") { ActionLabel(AppSymbol.CLOSE, "Cancel operation") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
                if (state.file != null) {
                    OutlinedButton(onClick = model::share, enabled = idle, modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.SHARE, "Share PDF") }
                    Button(onClick = {
                        model.beginSave()?.let { name ->
                            try { save.launch(name) } catch (_: Exception) { model.saveLaunchFailed() }
                        }
                    }, enabled = idle, modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.SAVE, "Save PDF") }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss, enabled = idle) { Text("Close") } })
}
