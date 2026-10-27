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
import com.lazarus.personalojtdtr.util.hoursLabel
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private class CreateDtrDocument : ActivityResultContracts.CreateDocument("application/pdf") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable fun DtrExportDialog(model: DtrExportViewModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(remember { CreateDtrDocument() }, model::saveResult)
    var months by remember { mutableStateOf(false) }
    val idle = state.busy == null
    AlertDialog(onDismissRequest = { if (idle) onDismiss() },
        title = { DialogSystemBars(); Text("Export DTR") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Offline PDF · A4 · Philippine time")
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
                OutlinedButton(onClick = model::preview, enabled = idle, modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.HISTORY, "Preview period") }
                state.report?.let { report ->
                    Text(report.periodLabel, style = MaterialTheme.typography.titleMedium)
                    Text(report.student)
                    Text("${report.days} recorded attendance days · ${report.total.hoursLabel()}")
                    if (report.days == 0) Text("No attendance records for this period. Choose another month.")
                    else {
                        Text("${report.incompleteDays} days with missing timestamps. ${report.notes.size} record notes / legacy details.")
                        Text("Missing times stay blank. Only completed intervals earn hours. Legacy sessions are listed separately.", style = MaterialTheme.typography.bodySmall)
                        Text("This preview is a saved-data snapshot. Preview again to include newer records.", style = MaterialTheme.typography.bodySmall)
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
