package com.lazarus.personalojtdtr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Visible text names the action; its paired icon is decorative to accessibility services. */
@Composable fun ActionLabel(symbol: AppSymbol, text: String, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(symbol, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
        Text(text, Modifier.weight(1f, fill = false))
    }
}

/** A small glyph always has a full touch target and an explicit spoken action. */
@Composable fun ActionIconButton(symbol: AppSymbol, description: String, onClick: () -> Unit,
    enabled: Boolean = true, destructive: Boolean = false) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current)) {
        AppIcon(symbol, description, Modifier.size(24.dp))
    }
}

/** Error tint belongs to the enabled button's content, so disabled icons/text remain muted. */
@Composable fun DestructiveButton(onClick: () -> Unit, enabled: Boolean = true, text: String, symbol: AppSymbol = AppSymbol.DELETE) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        ActionLabel(symbol, text, compact = true)
    }
}
