package com.lazarus.personalojtdtr.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lazarus.personalojtdtr.domain.model.WidgetSnapshot
import com.lazarus.personalojtdtr.util.WidgetPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.TimeUnit

class OfflineWidgetPublisher(context: Context) : WidgetPublisher {
    private val app = context.applicationContext
    override fun refresh() { WidgetUpdates.request(app) }
}

object WidgetUpdates {
    internal val revisionKey = longPreferencesKey("room_refresh_revision")
    private const val WORK = "ojt-widget-refresh"
    internal const val MIDNIGHT = "com.lazarus.personalojtdtr.widget.MIDNIGHT"

    fun installed(context: Context): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, OjtWidgetReceiver::class.java)).isNotEmpty()

    fun request(context: Context): Operation? {
        if (!installed(context)) return null
        // Serialize refreshes so a change during an active read gets a subsequent update.
        // No network/charging constraints and no attendance content in WorkManager input.
        return WorkManager.getInstance(context).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        )
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, WidgetClockReceiver::class.java).setAction(MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = midnightIntent(context)
        if (!installed(context)) { alarms.cancel(intent); return }
        // Non-wakeup, inexact: midnight refresh may wait for the device to leave idle.
        // No exact-alarm permission, foreground service, or repeating polling loop.
        alarms.set(AlarmManager.RTC, WidgetSnapshot.nextMidnight(Instant.now()).toEpochMilli(), intent)
    }

    fun disabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!WidgetUpdates.installed(applicationContext)) return Result.success()
        return try {
            val widget = OjtProgressWidget()
            val ids = GlanceAppWidgetManager(applicationContext).getGlanceIds(OjtProgressWidget::class.java)
            for (id in ids) {
                // A revision invalidates a live Glance composition even when Room has not
                // changed (midnight/clock/theme refresh). Attendance stays only in Room.
                updateAppWidgetState(applicationContext, id) { state ->
                    state[WidgetUpdates.revisionKey] = (state[WidgetUpdates.revisionKey] ?: 0L) + 1L
                }
                widget.update(applicationContext, id)
            }
            WidgetUpdates.scheduleMidnight(applicationContext)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Never include database rows, photo paths, or exceptions in worker output/logs.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(WidgetUpdates.MIDNIGHT, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                androidx.work.WorkManager.getInstance(app).enqueueUniqueWork("attendance-reminder-reconcile-now",
                    androidx.work.ExistingWorkPolicy.REPLACE, androidx.work.OneTimeWorkRequestBuilder<com.lazarus.personalojtdtr.reminders.ReminderReconcileWorker>().build())
                WidgetUpdates.scheduleMidnight(app)
                // Keep the receiver alive until the refresh request is durably enqueued.
                WidgetUpdates.request(app)?.result?.get(8, TimeUnit.SECONDS)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The daily platform update/app foreground read remains a reconciliation path.
            } finally { pending.finish() }
        }
    }
}
