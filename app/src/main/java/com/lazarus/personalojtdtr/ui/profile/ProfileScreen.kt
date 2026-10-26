package com.lazarus.personalojtdtr.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.lazarus.personalojtdtr.domain.model.ThemeMode
import com.lazarus.personalojtdtr.ui.theme.ThemeSettings
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.lazarus.personalojtdtr.domain.model.PersonalProfile
import com.lazarus.personalojtdtr.ui.*
import java.time.Duration
import java.time.LocalDate

@Composable fun ProfileScreen(profile: PersonalProfile, busy: Boolean, onSave: (PersonalProfile) -> Unit, onExport: () -> Unit, onRestore: () -> Unit, appearance: ThemeSettings, onThemeSelected: (ThemeMode) -> Unit) {
    var chooseTheme by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
    var hours by rememberSaveable(profile.requiredHours) { mutableStateOf(profile.requiredHours.toString()) }
    var breakText by rememberSaveable(profile.defaultBreak) { mutableStateOf(profile.defaultBreak.toMinutes().toString()) }
    var startText by rememberSaveable(profile.startDate) { mutableStateOf(profile.startDate?.toString().orEmpty()) }
    val requiredHours = hours.toLongOrNull()
    val breakMinutes = breakText.toLongOrNull()
    val startDate = runCatching { LocalDate.parse(startText) }.getOrNull()
    val valid = name.trim().length <= 100 && requiredHours != null && requiredHours in 0..100000 &&
        breakMinutes != null && breakMinutes in 0..1440 && (startText.isBlank() || startDate != null)
    Page("Profile & settings", "Private, offline and stored on this device.") {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Page), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(AppSymbol.PROFILE.vector, null, Modifier.padding(16.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Micro)) {
                Text(profile.displayName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge)
                Text("Your personal OJT workspace", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsGroup("Personal information", AppSymbol.PROFILE) {
            TextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), enabled = !busy, isError = name.trim().length > 100, colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
        }
        SettingsGroup("OJT requirements", AppSymbol.PROGRESS) {
            TextField(value = hours, onValueChange = { hours = it }, label = { Text("Required OJT hours") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !busy,
                isError = requiredHours == null || requiredHours !in 0..100000,
                supportingText = { Text("Whole hours, 0–100000. Zero means no goal is set.") }, colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
            TextField(value = startText, onValueChange = { startText = it }, label = { Text("Start date (optional, YYYY-MM-DD)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), enabled = !busy, isError = startText.isNotBlank() && startDate == null, colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
            TextField(value = breakText, onValueChange = { breakText = it }, label = { Text("Legacy break (minutes)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !busy,
                isError = breakMinutes == null || breakMinutes !in 0..1440,
                supportingText = { Text("For legacy P1 records only. Four-event attendance already excludes the lunch gap.") }, colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
            Button(onClick = {
                if (valid) onSave(PersonalProfile(name, requireNotNull(requiredHours), startDate, Duration.ofMinutes(requireNotNull(breakMinutes))))
            }, enabled = !busy && valid, modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.SAVE, "Save settings", compact = true) }
        }
        SettingsGroup("Appearance", AppSymbol.APPEARANCE) {
            SettingsActionRow("Theme", appearance.mode?.label ?: "Loading…", AppSymbol.APPEARANCE,
                enabled = !appearance.saving && appearance.mode != null) { chooseTheme = true }
            if (appearance.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            appearance.error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
        }
        SettingsGroup("Data & backup", AppSymbol.BACKUP) {
            Text("Appearance is stored separately on this device and is not included in backups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Includes profile, attendance, recovery actions, activities and referenced photos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsActionRow("Export backup", "Save an offline archive", AppSymbol.EXPORT, !busy, onExport)
            HorizontalDivider()
            SettingsActionRow("Restore backup", "Validate and preview before replacing data", AppSymbol.RESTORE, !busy, onRestore)
            Text("Backups are not automatically encrypted. Choose a secure local destination and protect the archive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Restore requires confirmation and replaces all current local records. There is no automatic merge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsGroup("Application information", AppSymbol.INFO) {
            Text("My OJT Companion", style = MaterialTheme.typography.titleMedium)
            Text("Offline attendance, photo evidence, activity journal and home-screen widget.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No account required. Photo sharing is always manual.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (chooseTheme) AlertDialog(
        onDismissRequest = { chooseTheme = false },
        title = { DialogSystemBars(); Text("Theme") },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().selectable(selected = appearance.mode == mode, role = Role.RadioButton,
                        onClick = { onThemeSelected(mode); chooseTheme = false }).heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = appearance.mode == mode, onClick = null)
                        Spacer(Modifier.width(AppSpacing.Related))
                        Text(mode.label)
                    }
                }
            }
        },
        confirmButton = { QuietButton(onClick = { chooseTheme = false }) { ActionLabel(AppSymbol.CLOSE, "Cancel", compact = true) } },
    )
}
