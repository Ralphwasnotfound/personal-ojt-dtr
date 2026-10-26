package com.lazarus.personalojtdtr.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.ui.attendance.LocalPhoto
import com.lazarus.personalojtdtr.ui.theme.AppRefinement
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import java.io.File

@Composable fun RefinedSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(AppRefinement.PanelRadius),
        color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
        shadowElevation = AppRefinement.PanelElevation,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(AppSpacing.Page), verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact), content = content)
    }
}

@Composable fun StatusPill(label: String, symbol: AppSymbol = AppSymbol.HISTORY, emphasized: Boolean = false) {
    Surface(shape = RoundedCornerShape(50), color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(symbol, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable fun SettingsGroup(title: String, symbol: AppSymbol, content: @Composable ColumnScope.() -> Unit) {
    RefinedSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Related), verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol.vector, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            SectionHeading(title)
        }
        content()
    }
}

@Composable fun PhotoEvidence(file: File?, description: String, onView: () -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = AppSpacing.Related), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Related), verticalAlignment = Alignment.CenterVertically) {
        LocalPhoto(file, description, Modifier.size(64.dp).clip(RoundedCornerShape(AppRefinement.ThumbnailRadius)), maxEdge = 320)
        Column(Modifier.weight(1f)) {
            Text("Photo evidence", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Micro)) {
                QuietButton(onView) { ActionLabel(AppSymbol.PHOTO, "View photos", compact = true) }
                QuietButton(onShare) { ActionLabel(AppSymbol.SHARE, "Share", compact = true) }
            }
        }
    }
}

@Composable fun RoundedProgress(progress: Float, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, track: Color = MaterialTheme.colorScheme.outlineVariant) {
    // Animate presentation only. All text and accessibility values stay at the actual
    // recorded total. Compose also observes the platform's animator duration scale.
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val displayed by animateFloatAsState(if (entered || !motionEnabled) progress else 0f,
        animationSpec = if (motionEnabled) tween(320, easing = FastOutSlowInEasing) else snap(), label = "OJT progress")
    Canvas(modifier.fillMaxWidth().height(8.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (displayed > 0f) drawRoundRect(color, size = Size(size.width * displayed.coerceIn(0f, 1f), size.height), cornerRadius = radius)
    }
}
