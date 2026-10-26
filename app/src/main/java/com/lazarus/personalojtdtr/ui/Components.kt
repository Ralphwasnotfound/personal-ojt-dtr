package com.lazarus.personalojtdtr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.ui.theme.AppSpacing

@Composable fun PageHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
        Box(Modifier.size(24.dp, 3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun Page(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(AppSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section)) {
        PageHeader(title, subtitle)
        content()
    }
}

@Composable fun SectionHeading(title: String, symbol: AppSymbol? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (symbol != null) {
            Icon(symbol.vector, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(AppSpacing.Related))
        }
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

@Composable fun Section(title: String, symbol: AppSymbol? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
        SectionHeading(title, symbol)
        content()
    }
}

@Composable fun EmptyState(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, actionSymbol: AppSymbol = AppSymbol.RIGHT) {
    Section(title) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) QuietButton(onClick = onAction) { ActionLabel(actionSymbol, actionLabel, compact = true) }
    }
}

@Composable fun SettingsActionRow(title: String, subtitle: String, symbol: AppSymbol, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 72.dp).padding(vertical = AppSpacing.Compact),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Page), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(symbol.vector, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Micro)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppIcon(AppSymbol.RIGHT, modifier = Modifier.size(18.dp))
    }
}

@Composable fun TimelineRow(title: String, detail: String, status: String, completed: Boolean,
    available: Boolean = false, last: Boolean = false, symbol: AppSymbol? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val indicator = if (completed || available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val onIndicator = if (completed || available) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth().drawBehind {
        if (!last) drawLine(line, Offset(16.dp.toPx(), 32.dp.toPx()), Offset(16.dp.toPx(), size.height), 1.dp.toPx())
    }) {
        Box(Modifier.padding(top = 2.dp).size(32.dp).background(indicator, CircleShape), contentAlignment = Alignment.Center) {
            if (symbol != null) Icon(symbol.vector, null, Modifier.size(18.dp), tint = onIndicator)
            else if (completed) Icon(AppSymbol.CHECK.vector, null, Modifier.size(18.dp), tint = onIndicator)
            else if (available) Icon(AppSymbol.CAMERA.vector, null, Modifier.size(18.dp), tint = onIndicator)
            else Text("·", color = onIndicator)
        }
        Column(Modifier.padding(start = 44.dp, bottom = AppSpacing.Section), verticalArrangement = Arrangement.spacedBy(AppSpacing.Micro)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            StatusPill(status, if (completed) AppSymbol.CHECK else if (available) AppSymbol.CAMERA else AppSymbol.HISTORY, available)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

// High-contrast maroon ink for small action labels in both themes. Filled primary
// buttons keep the palette accent; light-on-maroon button contrast is preserved.
@Composable fun QuietButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier.heightIn(min = AppSpacing.Touch), enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer), content = content)
}

@Composable fun SecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = AppSpacing.Touch), enabled, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer), content = content)
}

@Composable fun appTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    cursorColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
