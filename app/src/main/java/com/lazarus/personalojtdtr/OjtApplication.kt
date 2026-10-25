package com.lazarus.personalojtdtr

import android.app.Application
import android.util.Log
import com.lazarus.personalojtdtr.data.backup.OfflineBackupService
import kotlinx.coroutines.*
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.data.local.OjtDatabase
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.data.repository.ActivityJournal

class OjtApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try { backup.startup(); journal.startup(); recorder.recover() }
            catch (e: Exception) { Log.e("AttendanceStorage", "Private storage recovery deferred; no backup contents were logged.") }
        }
    }
    val journal by lazy { ActivityJournal(this, repository) }
    val backup by lazy { OfflineBackupService(this, repository) }
    val recorder by lazy { AttendanceRecorder(repository, LocalAttendancePhotos(filesDir, AttendanceImageProcessor::stamp)) }
    val repository by lazy { OjtRepository(OjtDatabase.create(this)) }
}
