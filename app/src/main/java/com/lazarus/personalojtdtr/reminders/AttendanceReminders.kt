package com.lazarus.personalojtdtr.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.lazarus.personalojtdtr.*
import com.lazarus.personalojtdtr.R
import com.lazarus.personalojtdtr.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*
import java.util.concurrent.TimeUnit

object AttendanceReminders {
    const val CHANNEL = "attendance_reminders"
    const val TAG = "attendance-reminder"
    fun channel(context: Context) { context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, "Attendance reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Optional morning and afternoon OJT reminders" }) }
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun notificationId(date: LocalDate, session: OjtSession) = (date.toEpochDay() * 2 + session.code).toInt()
    private fun name(key: String, settings: ReminderSettings) = "$TAG:$key:${settings.hashCode()}"
    suspend fun reconcile(context: Context) {
        channel(context)
        val app = context.applicationContext as OjtApplication
        val settings = app.reminderPreferences.settings.first()
        val inputs = app.repository.widgetInputs()
        val now = Instant.now(); val today = now.atZone(ReminderRules.zone).toLocalDate()
        val work = WorkManager.getInstance(context)
        val expected = mutableSetOf<String>()
        val validSessions = mutableSetOf<String>()
        for (date in listOf(today, today.plusDays(1))) for (session in OjtSession.entries) {
            val enabled = settings.enabled && (if (session == OjtSession.MORNING) settings.morningEnabled else settings.afternoonEnabled)
            if (!enabled || !ReminderRules.eligible(inputs, date, session) || !allowed(context)) {
                context.getSystemService(NotificationManager::class.java).cancel(notificationId(date, session)); continue
            }
            validSessions += "session:$date:${session.code}"
            for (occurrence in listOf(ReminderOccurrence.READY, ReminderOccurrence.FIRST, ReminderOccurrence.SECOND)) {
                if (occurrence == ReminderOccurrence.SECOND && !settings.secondEnabled) continue
                if (occurrence == ReminderOccurrence.READY && !(if (session == OjtSession.MORNING) settings.morningReady else settings.afternoonReady)) continue
                if (app.reminderPreferences.fired(date, session, occurrence)) continue
                val target = ReminderRules.target(date, session, occurrence, settings)
                if (occurrence == ReminderOccurrence.READY && now >= ReminderRules.target(date, session, ReminderOccurrence.FIRST, settings)) continue
                if (now > ReminderRules.target(date, session, ReminderOccurrence.FIRST, settings).plusSeconds(90 * 60)) continue
                val key = name(ReminderRules.key(date, session, occurrence), settings)
                expected += key
                work.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request(date, session, occurrence, settings, Duration.between(now, target).toMillis().coerceAtLeast(0), key))
            }
        }
        // Cancel pending obsolete settings/date/session jobs. Running deliveries recheck persisted state.
        work.getWorkInfosByTag(TAG).get().filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }.forEach { info ->
            val validSnooze = "snooze" in info.tags && info.tags.any { it in validSessions } && "settings:$settings" in info.tags
            if (info.tags.none { it in expected } && !validSnooze) work.cancelWorkById(info.id)
        }
        if (!settings.enabled) { work.cancelUniqueWork("attendance-reminder-reconcile"); return }
        work.enqueueUniquePeriodicWork("attendance-reminder-reconcile", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderReconcileWorker>(12, TimeUnit.HOURS).build())
    }
    fun request(date: LocalDate, session: OjtSession, occurrence: ReminderOccurrence, settings: ReminderSettings, delay: Long, tag: String) =
        OneTimeWorkRequestBuilder<ReminderDeliveryWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("date" to date.toString(), "session" to session.code, "occurrence" to occurrence.name, "settings" to settings.toString()))
            .addTag(TAG).addTag(tag).addTag("session:$date:${session.code}").addTag("settings:$settings").apply { if (occurrence == ReminderOccurrence.SNOOZE) addTag("snooze") }.build()
    fun intent(context: Context, date: LocalDate, session: OjtSession, action: String): PendingIntent = PendingIntent.getActivity(context,
        notificationId(date, session), Intent(context, MainActivity::class.java).setAction("com.lazarus.personalojtdtr.reminder.$action.${session.name}")
            .putExtra("reminder_date", date.toString()).putExtra("reminder_session", session.code)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class ReminderReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { AttendanceReminders.reconcile(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
}
class ReminderDeliveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { deliver() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    private suspend fun deliver(): Result {
        val app = applicationContext as OjtApplication
        val date = runCatching { LocalDate.parse(inputData.getString("date")) }.getOrNull() ?: return Result.success()
        val session = OjtSession.entries.firstOrNull { it.code == inputData.getInt("session", -1) } ?: return Result.success()
        val occurrence = runCatching { ReminderOccurrence.valueOf(inputData.getString("occurrence")!!) }.getOrNull() ?: return Result.success()
        val settings = app.reminderPreferences.settings.first()
        if (inputData.getString("settings") != settings.toString()) return Result.success()
        val inputs = app.repository.widgetInputs(); val now = Instant.now()
        if (!ReminderRules.deliverable(settings, inputs, date, session, occurrence, now, AttendanceReminders.allowed(app))) return Result.success()
        if (!app.reminderPreferences.claim(date, session, occurrence, now)) return Result.success()
        val title = if (occurrence == ReminderOccurrence.READY) "OJT Starts in ${((Duration.between(now, ReminderRules.target(date, session, ReminderOccurrence.FIRST, settings)).seconds + 59) / 60).coerceAtLeast(1)} Minutes" else "OJT ${session.label} Attendance"
        val message = if (occurrence == ReminderOccurrence.READY) "Your ${session.label.lowercase()} OJT starts in ${((Duration.between(now, ReminderRules.target(date, session, ReminderOccurrence.FIRST, settings)).seconds + 59) / 60).coerceAtLeast(1)} minutes. Get ready to record your Time In!" else if (session == OjtSession.AFTERNOON && WidgetSnapshot.safeCameraAction(date, inputs) == AttendanceEventType.MORNING_TIME_IN)
            "Afternoon In is missing. Review or explicitly skip morning attendance first. Open Attendance to choose the next valid action."
        else "You haven't recorded ${session.label} In yet. Are you attending or skipping this session?"
        val record = AttendanceReminders.intent(app, date, session, "RECORD")
        val builder = NotificationCompat.Builder(app, AttendanceReminders.CHANNEL).setSmallIcon(R.drawable.widget_ic_camera)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(record).setAutoCancel(true).addAction(0, "Record Attendance", record)
            .addAction(0, "Skip ${session.label}", AttendanceReminders.intent(app, date, session, "SKIP"))
        if (occurrence != ReminderOccurrence.SNOOZE && occurrence != ReminderOccurrence.READY) {
            val snooze = PendingIntent.getBroadcast(app, AttendanceReminders.notificationId(date, session), Intent(app, ReminderSnoozeReceiver::class.java)
                .setAction("snooze:$date:${session.code}").putExtra("date", date.toString()).putExtra("session", session.code), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "Remind Later", snooze)
        }
        // Permission and channel state can change after the first check.
        val stillEligible = settings == app.reminderPreferences.settings.first() && ReminderRules.deliverable(
            settings, app.repository.widgetInputs(), date, session, occurrence, Instant.now(), AttendanceReminders.allowed(app))
        if (stillEligible) try { NotificationManagerCompat.from(app).notify(AttendanceReminders.notificationId(date, session), builder.build()) } catch (_: SecurityException) { }
        return Result.success()
    }
}
class ReminderSnoozeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as OjtApplication
                val date = LocalDate.parse(intent.getStringExtra("date"))
                val session = OjtSession.entries.single { it.code == intent.getIntExtra("session", -1) }
                val settings = app.reminderPreferences.settings.first(); val now = Instant.now()
                if (ReminderRules.deliverable(settings, app.repository.widgetInputs(), date, session, ReminderOccurrence.SNOOZE, now, AttendanceReminders.allowed(app)) && app.reminderPreferences.claimSnooze(date, session)) {
                    val key = "attendance-snooze:$date:${session.code}"
                    WorkManager.getInstance(app).enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, AttendanceReminders.request(date, session, ReminderOccurrence.SNOOZE, settings, 10 * 60_000L, key)).result.get()
                    app.getSystemService(NotificationManager::class.java).cancel(AttendanceReminders.notificationId(date, session))
                }
            } catch (_: Exception) { } finally { pending.finish() }
        }
    }
}
