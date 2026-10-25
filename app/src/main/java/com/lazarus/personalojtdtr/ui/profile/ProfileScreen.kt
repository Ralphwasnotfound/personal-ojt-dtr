package com.lazarus.personalojtdtr.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.lazarus.personalojtdtr.domain.model.PersonalProfile
import com.lazarus.personalojtdtr.ui.*
import java.time.Duration
import java.time.LocalDate

@Composable fun ProfileScreen(profile: PersonalProfile, busy: Boolean, onSave: (PersonalProfile) -> Unit, onExport: () -> Unit, onRestore: () -> Unit) {
    var name by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
    var hours by rememberSaveable(profile.requiredHours) { mutableStateOf(profile.requiredHours.toString()) }
    var breakText by rememberSaveable(profile.defaultBreak) { mutableStateOf(profile.defaultBreak.toMinutes().toString()) }
    var startText by rememberSaveable(profile.startDate) { mutableStateOf(profile.startDate?.toString().orEmpty()) }
    val requiredHours = hours.toLongOrNull()
    val breakMinutes = breakText.toLongOrNull()
    val startDate = runCatching { LocalDate.parse(startText) }.getOrNull()
    val valid = name.trim().length <= 100 && requiredHours != null && requiredHours in 0..100000 &&
        breakMinutes != null && breakMinutes in 0..1440 && (startText.isBlank() || startDate != null)
    Page("Profile & settings", "Stored only on this device. No account is required.") {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = name.trim().length > 100)
        OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Required OJT hours") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), isError = requiredHours == null || requiredHours !in 0..100000)
        Text("Use whole hours from 0 to 100000. Zero means no goal has been set.")
        OutlinedTextField(value = breakText, onValueChange = { breakText = it }, label = { Text("Saved legacy break (minutes)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), isError = breakMinutes == null || breakMinutes !in 0..1440)
        Text("Preserved for legacy P1 records. Four-event attendance excludes the lunch gap without any additional break deduction.")
        OutlinedTextField(value = startText, onValueChange = { startText = it }, label = { Text("OJT start date (optional, YYYY-MM-DD)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), isError = startText.isNotBlank() && startDate == null)
        Button(onClick = {
            if (valid) onSave(PersonalProfile(name, requireNotNull(requiredHours), startDate, Duration.ofMinutes(requireNotNull(breakMinutes))))
        }, enabled = !busy && valid) { Text("Save settings") }
        InfoCard("Offline backup & restore") {
            Text("Export includes your profile/settings, attendance, recovery actions, activities and referenced photos.")
            Text("Backups are NOT automatically encrypted. Choose a secure local destination and protect the archive.")
            Text("Restore validates the archive and shows a preview. Confirming REPLACE overwrites all current local records; there is no automatic merge.")
            Button(onClick = onExport, enabled = !busy) { Text("Export Backup") }
            OutlinedButton(onClick = onRestore, enabled = !busy) { Text("Restore Backup") }
        }
        InfoCard("Planned integrations") {
            Text("Camera photos, timestamped images, and optional manual sharing are available from Attendance and History. Local backup/restore is available above. Home-screen widgets remain planned.")
        }
    }
}
