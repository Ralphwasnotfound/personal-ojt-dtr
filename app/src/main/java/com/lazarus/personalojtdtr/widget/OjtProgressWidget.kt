package com.lazarus.personalojtdtr.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lazarus.personalojtdtr.MainActivity
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.R
import com.lazarus.personalojtdtr.domain.model.WidgetDestination
import com.lazarus.personalojtdtr.domain.model.WidgetInputs
import com.lazarus.personalojtdtr.domain.model.WidgetSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant

class OjtProgressWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 148.dp), DpSize(250.dp, 224.dp), DpSize(320.dp, 280.dp)))
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as OjtApplication).repository
        val initial = try { repository.widgetInputs() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        val changes = repository.widgetChanges.map<WidgetInputs, WidgetInputs?> { it }.catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        WidgetUpdates.scheduleMidnight(context)
        provideContent {
            val inputs by changes.collectAsState(initial)
            val revision = currentState<Preferences>()[WidgetUpdates.revisionKey]
            val snapshot = remember(inputs, revision) { inputs?.let { WidgetSnapshot.from(it, Instant.now()) } }
            WidgetContent(snapshot)
        }
    }
}

class OjtWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OjtProgressWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdates.scheduleMidnight(context)
        WidgetUpdates.request(context)
    }
    override fun onEnabled(context: Context) { WidgetUpdates.scheduleMidnight(context) }
    override fun onDisabled(context: Context) { WidgetUpdates.disabled(context) }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot?) {
    val size = LocalSize.current
    val large = size.height >= 224.dp
    val spacious = size.height >= 280.dp
    val text = ColorProvider(R.color.widget_text)
    val muted = ColorProvider(R.color.widget_muted)
    Column(GlanceModifier.fillMaxSize().appWidgetBackground()
        .background(ImageProvider(R.drawable.widget_background))
        .padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 16.dp else 8.dp)) {
        Text("My OJT Companion", style = TextStyle(color = muted, fontSize = 12.sp,
            fontWeight = FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.height(if (spacious) 12.dp else 8.dp))
        if (snapshot != null) {
            val completed = snapshot.completedLabel.compactHours()
            val required = snapshot.requiredLabel.compactHours()
            val inlineGoal = completed.length <= 8 && required.length <= 7
            if (spacious) Text("Completed OJT hours", style = TextStyle(color = muted, fontSize = 11.sp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(completed, style = TextStyle(color = text,
                    fontSize = if (spacious) 36.sp else if (large) 30.sp else 24.sp,
                    fontWeight = FontWeight.Bold), maxLines = 1)
                // Long minute-precision values get their goal on a separate line in large
                // layouts; compact layouts prioritize the completed value over the goal.
                if (inlineGoal) {
                    Text(" / $required", style = TextStyle(color = muted,
                        fontSize = if (large) 16.sp else 12.sp), maxLines = 1)
                }
            }
            if (large) {
                val secondary = if (snapshot.summary.required.isZero) snapshot.goalLabel
                    else "${snapshot.remainingLabel.compactHours()} remaining" + if (inlineGoal) "" else " · Goal $required"
                Text(secondary, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(if (spacious) 12.dp else 8.dp))
            Progress(snapshot)
            if (large) {
                Spacer(GlanceModifier.height(if (spacious) 12.dp else 8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Text("Today's Attendance", GlanceModifier.defaultWeight(),
                        style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
                    Text(snapshot.dateLabel, style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
                }
                Text(snapshot.status.label, style = TextStyle(color = text, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium), maxLines = 1)
            }
        } else {
            Text("Open the app to reload local progress", style = TextStyle(color = text, fontSize = 14.sp), maxLines = 2)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!large && snapshot != null) {
                Column(GlanceModifier.defaultWeight()) {
                    // Keep the snapshot date visible when Android delays a midnight refresh.
                    Text("Today · ${snapshot.dateLabel}", style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 1)
                    Text(snapshot.status.label, style = TextStyle(color = text, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium), maxLines = 2)
                }
            }
            Shortcut("Attendance", WidgetDestination.ATTENDANCE,
                if (large || snapshot == null) GlanceModifier.defaultWeight() else GlanceModifier.width(48.dp), large || snapshot == null)
            if (large || snapshot == null) Spacer(GlanceModifier.width(8.dp))
            Shortcut("Activities", WidgetDestination.ACTIVITIES,
                if (large || snapshot == null) GlanceModifier.defaultWeight() else GlanceModifier.width(48.dp), large || snapshot == null)
        }
    }
}

// Presentation only: retain minute precision when nonzero, and avoid redundant "0m".
private fun String.compactHours(): String = removeSuffix(" 0m")

@Composable
private fun Progress(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_progress).apply {
        setProgressBar(R.id.widget_progress_bar, 10000, (snapshot.summary.progress * 10000).toInt(), false)
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // XML drawables provide rounded 4dp progress on API 26+, without animations.
        AndroidRemoteViews(views, GlanceModifier.defaultWeight().height(4.dp))
        Spacer(GlanceModifier.width(8.dp))
        Text(snapshot.percentageLabel, style = TextStyle(color = ColorProvider(R.color.widget_muted),
            fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun Shortcut(label: String, destination: WidgetDestination, modifier: GlanceModifier, showLabel: Boolean) {
    val context = LocalContext.current
    val intent = Intent(context, MainActivity::class.java).setAction(destination.action)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val icon = if (destination == WidgetDestination.ATTENDANCE) R.drawable.widget_ic_attendance else R.drawable.widget_ic_activities
    Box(modifier.height(48.dp).background(ImageProvider(R.drawable.widget_button))
        .clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(icon), contentDescription = if (showLabel) null else label,
                modifier = GlanceModifier.size(16.dp))
            if (showLabel) {
                Spacer(GlanceModifier.width(8.dp))
                Text(label, style = TextStyle(color = ColorProvider(R.color.widget_text),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
        }
    }
}
