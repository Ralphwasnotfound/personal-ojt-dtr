package com.lazarus.personalojtdtr.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.domain.model.PersonalProfile
import com.lazarus.personalojtdtr.domain.model.ThemeMode
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import com.lazarus.personalojtdtr.ui.theme.ThemeSettings
import java.time.Duration
import java.time.LocalDate
import java.util.Locale

@Composable fun ProfileScreen(profile: PersonalProfile, onEditProfile: () -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val displayName = profile.displayName.trim().ifBlank { "Your profile" }
    val initials = profile.displayName.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "?" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = colors.surface,
            border = BorderStroke(0.5.dp, colors.outlineVariant), shadowElevation = 0.5.dp) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = colors.primaryContainer) {
                    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                        Text(initials.uppercase(Locale.ROOT), style = MaterialTheme.typography.titleLarge,
                            color = colors.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() })
                    Text("Student profile", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = colors.tertiaryContainer) {
                    Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${profile.requiredHours}", style = MaterialTheme.typography.titleSmall,
                            color = colors.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                        Text("hours", style = MaterialTheme.typography.labelSmall,
                            color = colors.onTertiaryContainer)
                    }
                }
            }
        }

        ProfileGroup("OJT information", AppSymbol.PROGRESS) {
            ProfileInfoRow(AppSymbol.REMAINING, "Required hours", "${profile.requiredHours} hours")
            HorizontalDivider(color = colors.outlineVariant)
            ProfileInfoRow(AppSymbol.CALENDAR, "Start date", profile.startDate?.toString() ?: "Not set")
            HorizontalDivider(color = colors.outlineVariant)
            ProfileInfoRow(AppSymbol.ATTENDANCE, "Legacy P1 break", "${profile.defaultBreak.toMinutes()} minutes")
        }

        ProfileGroup("Profile", AppSymbol.PROFILE) {
            SettingsActionRow("Edit profile", "Personal and OJT details", AppSymbol.EDIT, onClick = onEditProfile)
            HorizontalDivider(color = colors.outlineVariant)
            SettingsActionRow("Settings", "Appearance, backup and restore", AppSymbol.SETTINGS,
                onClick = onSettings)
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(AppSymbol.INFO, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("My OJT Companion", style = MaterialTheme.typography.titleSmall)
                Text("Offline attendance, photo evidence, activity journal and widget.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text("No account required · photo sharing is manual.", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable fun EditProfileScreen(profile: PersonalProfile, busy: Boolean, onSave: (PersonalProfile) -> Unit,
    onBack: () -> Unit) {
    var name by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
    var hours by rememberSaveable(profile.requiredHours) { mutableStateOf(profile.requiredHours.toString()) }
    var breakText by rememberSaveable(profile.defaultBreak) { mutableStateOf(profile.defaultBreak.toMinutes().toString()) }
    var startText by rememberSaveable(profile.startDate) { mutableStateOf(profile.startDate?.toString().orEmpty()) }
    val requiredHours = hours.toLongOrNull()
    val breakMinutes = breakText.toLongOrNull()
    val startDate = runCatching { LocalDate.parse(startText) }.getOrNull()
    val valid = name.trim().length <= 100 && requiredHours != null && requiredHours in 0..100000 &&
        breakMinutes != null && breakMinutes in 0..1440 && (startText.isBlank() || startDate != null)

    ProfileSubPage("Edit profile", "Student and OJT details", "Back to profile", onBack) {
        ProfileGroup("Student details", AppSymbol.PROFILE) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") },
                leadingIcon = { AppIcon(AppSymbol.PROFILE, modifier = Modifier.size(20.dp)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                isError = name.trim().length > 100,
                supportingText = { if (name.trim().length > 100) Text("Use 100 characters or fewer") },
                shape = MaterialTheme.shapes.medium)
        }
        ProfileGroup("OJT setup", AppSymbol.PROGRESS) {
            OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Required OJT hours") },
                leadingIcon = { AppIcon(AppSymbol.PROGRESS, modifier = Modifier.size(20.dp)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), enabled = !busy,
                isError = requiredHours == null || requiredHours !in 0..100000,
                supportingText = { if (requiredHours == null || requiredHours !in 0..100000) Text("Enter a whole number from 0 to 100000") },
                shape = MaterialTheme.shapes.medium)
            OutlinedTextField(value = startText, onValueChange = { startText = it },
                label = { Text("Start date · optional") },
                leadingIcon = { AppIcon(AppSymbol.CALENDAR, modifier = Modifier.size(20.dp)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                isError = startText.isNotBlank() && startDate == null,
                supportingText = { if (startText.isNotBlank() && startDate == null) Text("Use YYYY-MM-DD") },
                shape = MaterialTheme.shapes.medium)
            OutlinedTextField(value = breakText, onValueChange = { breakText = it },
                label = { Text("Legacy P1 break · minutes") },
                leadingIcon = { AppIcon(AppSymbol.ATTENDANCE, modifier = Modifier.size(20.dp)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), enabled = !busy,
                isError = breakMinutes == null || breakMinutes !in 0..1440,
                supportingText = {
                    if (breakMinutes == null || breakMinutes !in 0..1440) Text("Enter a value from 0 to 1440 minutes")
                    else Text("Used for legacy P1 records")
                }, shape = MaterialTheme.shapes.medium)
        }
        Button(onClick = {
            if (valid) onSave(PersonalProfile(name, requireNotNull(requiredHours), startDate,
                Duration.ofMinutes(requireNotNull(breakMinutes))))
        }, enabled = !busy && valid, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp)) {
            ActionLabel(AppSymbol.SAVE, if (busy) "Saving…" else "Save profile")
        }
    }
}

@Composable fun SettingsScreen(onAppearance: () -> Unit, onDataBackup: () -> Unit, onBack: () -> Unit) {
    ProfileSubPage("Settings", "Personalize and protect your local data", "Back to profile", onBack) {
        ProfileGroup("Preferences", AppSymbol.SETTINGS) {
            SettingsActionRow("Appearance", "System default, light or dark", AppSymbol.APPEARANCE,
                onClick = onAppearance)
        }
        ProfileGroup("Storage", AppSymbol.BACKUP) {
            SettingsActionRow("Data & Backup", "Export or restore your records", AppSymbol.BACKUP,
                onClick = onDataBackup)
        }
    }
}

@Composable fun AppearanceSettingsScreen(appearance: ThemeSettings, onThemeSelected: (ThemeMode) -> Unit,
    onBack: () -> Unit) {
    ProfileSubPage("Appearance", "Choose how My OJT Companion looks", "Back to settings", onBack) {
        ProfileGroup("Theme", AppSymbol.APPEARANCE) {
            if (appearance.mode == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().selectable(selected = appearance.mode == mode,
                            enabled = !appearance.saving, role = Role.RadioButton,
                            onClick = { onThemeSelected(mode) }).heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = appearance.mode == mode, onClick = null,
                                enabled = !appearance.saving)
                            Spacer(Modifier.width(AppSpacing.Related))
                            Text(mode.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (appearance.mode == mode) AppIcon(AppSymbol.CHECK,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            if (appearance.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            appearance.error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable fun DataBackupSettingsScreen(busy: Boolean, onExport: () -> Unit, onRestore: () -> Unit,
    onBack: () -> Unit) {
    ProfileSubPage("Data & Backup", "Manage your offline records", "Back to settings", onBack) {
        ProfileGroup("Local backup", AppSymbol.BACKUP) {
            Text("Includes profile, attendance, recovery, activities and photos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsActionRow("Export backup", "Save a copy of local records", AppSymbol.EXPORT,
                enabled = !busy, onClick = onExport)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsActionRow("Restore backup", "Preview before replacing local data", AppSymbol.RESTORE,
                enabled = !busy, onClick = onRestore)
        }
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top) {
                AppIcon(AppSymbol.INFO, modifier = Modifier.size(20.dp))
                Text("Archives are not encrypted. Restore validates the archive and asks for confirmation before replacing local data.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable private fun ProfileSubPage(title: String, subtitle: String, backLabel: String, onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(AppSpacing.Page), verticalArrangement = Arrangement.spacedBy(AppSpacing.Section)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIconButton(AppSymbol.BACK, backLabel, onBack)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable private fun ProfileGroup(title: String, symbol: AppSymbol, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AppIcon(symbol, modifier = Modifier.size(19.dp))
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable private fun ProfileInfoRow(symbol: AppSymbol, label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(symbol, modifier = Modifier.size(18.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    }
}
