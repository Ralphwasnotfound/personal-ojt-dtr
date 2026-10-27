package com.lazarus.personalojtdtr.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazarus.personalojtdtr.ui.dashboard.*

/** Neutral shared navigation; only the active tab receives a tonal Material indicator. */
@Composable fun DashboardBottomBar(destinations: List<Pair<String, String>>, selected: (String) -> Boolean,
    enabled: Boolean, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Use one fitted label size for all tabs. Preserve full names instead of splitting
        // single words at large font scales; page content keeps the requested font scale.
        val labelSize = with(LocalDensity.current) {
            val available = (maxWidth.toPx() / destinations.size - 12.dp.toPx()).coerceAtLeast(1f)
            val widest = destinations.maxOf { (_, label) ->
                measurer.measure(AnnotatedString(label), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    softWrap = false, maxLines = 1).size.width
            }.toFloat().coerceAtLeast(1f)
            (11f * minOf(1f, available / widest)).sp
        }
        Column {
            HorizontalDivider(color = colors.outlineVariant, thickness = 0.5.dp)
            NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                destinations.forEach { (route, label) ->
                    val active = selected(route)
                    NavigationBarItem(selected = active, enabled = enabled, onClick = { onSelect(route) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = dashboardAccent(), selectedTextColor = dashboardAccent(),
                            indicatorColor = colors.primaryContainer,
                            unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant),
                        icon = { DashboardIcon(when (route) {
                            "dashboard" -> DashboardGlyph.HOME
                            "attendance" -> DashboardGlyph.CLOCK
                            "activities" -> DashboardGlyph.ACTIVITIES
                            "history" -> DashboardGlyph.HISTORY
                            else -> DashboardGlyph.PROFILE
                        }, Modifier.size(23.dp), if (!enabled) colors.onSurface.copy(alpha = 0.38f) else if (active) dashboardAccent() else colors.onSurfaceVariant) },
                        label = { Text(label, fontSize = labelSize, lineHeight = labelSize * 1.3f, maxLines = 1, softWrap = false,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal) })
                }
            }
        }
    }
}
