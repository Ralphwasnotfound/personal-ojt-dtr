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
import com.lazarus.personalojtdtr.domain.usecase.ForecastMethod
import com.lazarus.personalojtdtr.util.hoursLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

class OjtProgressWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 148.dp), DpSize(250.dp, 224.dp), DpSize(320.dp, 460.dp)))
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
            // Read Manila time on every refresh/recomposition, including delayed midnight work.
            val snapshot = inputs?.let { WidgetSnapshot.from(it, Instant.now()) }
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
    val layout = widgetLayoutSize(size.height.value, size.width.value)
    val full = layout == WidgetLayoutSize.LARGE
    val medium = layout != WidgetLayoutSize.COMPACT
    val text = ColorProvider(R.color.widget_text)
    val muted = ColorProvider(R.color.widget_muted)
    Column(GlanceModifier.fillMaxSize().appWidgetBackground()
        .background(ImageProvider(R.drawable.widget_background)).padding(if (full) 16.dp else 10.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.widget_ic_activities), "OJT", GlanceModifier.size(if (full) 24.dp else 16.dp))
            Spacer(GlanceModifier.width(6.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text("MY OJT COMPANION", style = TextStyle(color = ColorProvider(R.color.widget_accent),
                    fontSize = if (full) 12.sp else 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (full) Text("OJT Progress", style = TextStyle(color = muted, fontSize = 11.sp))
            }
            if (full && snapshot != null) StatusPill(snapshot.status.label)
        }
        if (snapshot != null) {
            Column(GlanceModifier.fillMaxWidth()) {
            Spacer(GlanceModifier.height(if (full) 16.dp else 4.dp))
            if (full) Text("Completed time", style = TextStyle(color = muted, fontSize = 12.sp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(snapshot.completedLabel, GlanceModifier.defaultWeight(), style = TextStyle(color = text,
                    fontSize = if (full) 32.sp else 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format(Locale.ENGLISH, "%.1f%%", snapshot.summary.progress * 100),
                        style = TextStyle(color = ColorProvider(R.color.widget_accent), fontSize = if (full) 22.sp else 13.sp, fontWeight = FontWeight.Bold))
                    if (full) Text("of ${snapshot.requiredLabel}", style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(if (full) 8.dp else 2.dp))
            Progress(snapshot)
            }
            if (full) {
                Column(GlanceModifier.fillMaxWidth()) {
                Spacer(GlanceModifier.height(16.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    MetricTile("Days recorded", "${snapshot.completedDays} days", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(10.dp))
                    MetricTile("Remaining", snapshot.remainingLabel, GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(12.dp))
                Column(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_button)).padding(12.dp)) {
                    Text("Estimated completion", style = TextStyle(color = muted, fontSize = 11.sp))
                    Text(forecastLabel(snapshot).removePrefix("Estimated completion · "), style = TextStyle(
                        color = ColorProvider(R.color.widget_gold), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    snapshot.forecast.averageDailyCredit?.let { average ->
                        Text("${average.hoursLabel()} observed/day" + (snapshot.forecast.estimatedRemainingWorkdays?.let { " · $it workdays left" } ?: ""),
                            style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 2)
                    }
                    if (snapshot.forecast.method == ForecastMethod.MANUAL_OVERRIDE)
                        Text("Manual pace override", style = TextStyle(color = muted, fontSize = 10.sp))
                }
                Spacer(GlanceModifier.height(12.dp))
                Text("Today's Attendance", style = TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(snapshot.nextActionLabel, GlanceModifier.defaultWeight(), style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 2)
                    StatusPill(snapshot.status.label)
                }
                }
            } else {
                Column(GlanceModifier.fillMaxWidth()) {
                if (medium) {
                    Spacer(GlanceModifier.height(10.dp))
                    MetricTile("Remaining", snapshot.remainingLabel, GlanceModifier.fillMaxWidth())
                    Spacer(GlanceModifier.height(6.dp))
                }
                Text(if (snapshot.status == com.lazarus.personalojtdtr.domain.model.WidgetAttendanceStatus.DAY_OFF) "Day Off · No attendance required today" else "Today · ${snapshot.status.label}", style = TextStyle(color = text, fontSize = 10.sp), maxLines = 2)
                }
            }
        } else Text("Open the app to reload progress", style = TextStyle(color = text, fontSize = 12.sp), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Shortcut("Attendance", WidgetDestination.ATTENDANCE, GlanceModifier.defaultWeight(), true, camera = true)
            Spacer(GlanceModifier.width(8.dp))
            Shortcut("Add Activity", WidgetDestination.ACTIVITIES, GlanceModifier.defaultWeight(), true)
            Spacer(GlanceModifier.width(8.dp))
            Shortcut("Manage Workday",
                WidgetDestination.DAY_OFF, GlanceModifier.defaultWeight(), true)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: GlanceModifier) {
    Column(modifier.background(ImageProvider(R.drawable.widget_button)).padding(10.dp)) {
        Text(label, style = TextStyle(color = ColorProvider(R.color.widget_muted), fontSize = 11.sp), maxLines = 1)
        Text(value, style = TextStyle(color = ColorProvider(R.color.widget_text), fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun StatusPill(label: String) {
    Box(GlanceModifier.background(ImageProvider(R.drawable.widget_button)).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Text(label, style = TextStyle(color = ColorProvider(R.color.widget_accent), fontSize = 10.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

private fun forecastLabel(snapshot: WidgetSnapshot): String = when (snapshot.forecast.method) {
    ForecastMethod.COMPLETED -> "OJT completed"
    ForecastMethod.NO_REQUIREMENT -> "Set required hours"
    ForecastMethod.INSUFFICIENT_HISTORY -> "Collecting attendance data"
    ForecastMethod.SCHEDULE_REQUIRED -> "Set schedule"
    ForecastMethod.PROJECTION_LIMIT -> "Estimate unavailable · check schedule"
    ForecastMethod.AUTOMATIC, ForecastMethod.MANUAL_OVERRIDE -> snapshot.estimatedCompletionDate?.let {
        "Estimated completion · ${it.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))}"
    } ?: "Estimate unavailable · check schedule"
}

@Composable
private fun Progress(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_progress).apply {
        setProgressBar(R.id.widget_progress_bar, 10000, (snapshot.summary.progress * 10000).toInt(), false)
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // XML drawables provide rounded 4dp progress on API 26+, without animations.
        AndroidRemoteViews(views, GlanceModifier.defaultWeight().height(4.dp))
    }
}

@Composable
private fun Shortcut(label: String, destination: WidgetDestination, modifier: GlanceModifier, showLabel: Boolean, camera: Boolean = false) {
    val context = LocalContext.current
    val intent = Intent(context, MainActivity::class.java).setAction(destination.action)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("widget_camera", camera)
    val icon = when (destination) {
        WidgetDestination.ATTENDANCE -> R.drawable.widget_ic_camera
        WidgetDestination.ACTIVITIES -> R.drawable.widget_ic_activities
        WidgetDestination.DAY_OFF -> R.drawable.widget_ic_day_off
    }
    Box(modifier.height(48.dp).background(ImageProvider(R.drawable.widget_button))
        .clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(ImageProvider(icon), contentDescription = if (showLabel) null else label,
                modifier = GlanceModifier.size(16.dp))
            if (showLabel) {
                Spacer(GlanceModifier.height(2.dp))
                Text(label, style = TextStyle(color = ColorProvider(R.color.widget_text),
                    fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 2)
            }
        }
    }
}
