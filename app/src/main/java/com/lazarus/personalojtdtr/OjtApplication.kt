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
import kotlinx.coroutines.flow.collect

class OjtApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try { backup.startup(); journal.startup(); recorder.recover() }
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
    val journal by lazy { ActivityJournal(this, repository) }
    val backup by lazy { OfflineBackupService(this, repository) }
    val recorder by lazy { AttendanceRecorder(repository, LocalAttendancePhotos(filesDir, AttendanceImageProcessor::stamp) { directory ->
        val fd = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
    }) }
    val repository by lazy { OjtRepository(OjtDatabase.create(this)) }
}
