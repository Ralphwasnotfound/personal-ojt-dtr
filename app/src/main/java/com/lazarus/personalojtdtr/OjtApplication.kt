package com.lazarus.personalojtdtr

import android.app.Application
import android.util.Log
import com.lazarus.personalojtdtr.data.backup.OfflineBackupService
import kotlinx.coroutines.*
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.data.local.OjtDatabase
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.data.repository.ActivityJournal
import com.lazarus.personalojtdtr.widget.OfflineWidgetPublisher
import com.lazarus.personalojtdtr.data.preferences.OjtSchedulePreferences
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

class OjtApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reminderPreferences by lazy { com.lazarus.personalojtdtr.reminders.ReminderPreferences(this) }
    override fun onCreate() {
        super.onCreate()
        com.lazarus.personalojtdtr.reminders.AttendanceReminders.channel(this)
        applicationScope.launch {
            try {
                kotlinx.coroutines.flow.combine(repository.widgetChanges, reminderPreferences.settings) { _, _ -> Unit }.collect {
                    com.lazarus.personalojtdtr.reminders.AttendanceReminders.reconcile(this@OjtApplication)
                }
            } catch (_: Exception) { Log.w("AttendanceReminders", "Reminder scheduling deferred to next app start or background reconciliation.") }
        }
        applicationScope.launch {
            try {
                repository.migrateLegacySchedule(ojtSchedule.schedule.first())
                backup.startup(); journal.startup(); recorder.recover(); profileAvatars.startup()
            }
            catch (e: Exception) { Log.e("AttendanceStorage", "Private storage recovery deferred; no backup contents were logged.") }
        }
        applicationScope.launch {
            try { repository.widgetChanges.collect { widgetPublisher.refresh() } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { Log.w("OjtWidget", "Widget observation deferred; open the app to retry.") }
        }
    }
    val themePreferences by lazy { com.lazarus.personalojtdtr.data.preferences.ThemePreferences(this) }
    val widgetPublisher by lazy { OfflineWidgetPublisher(this) }
    val ojtSchedule by lazy { OjtSchedulePreferences(this) }
    val profileAvatars by lazy { ProfileAvatarManager(this, repository) }
    val journal by lazy { ActivityJournal(this, repository) }
    val backup by lazy { OfflineBackupService(this, repository) }
    val recorder by lazy { AttendanceRecorder(repository, LocalAttendancePhotos(filesDir, AttendanceImageProcessor::stamp) { directory ->
        val fd = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
    }) }
    val repository by lazy { OjtRepository(OjtDatabase.create(this), avatarFiles = LocalAvatarStore(filesDir, AvatarImages::validate) { directory ->
        val fd = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
    }) }
}
