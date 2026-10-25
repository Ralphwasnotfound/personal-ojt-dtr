package com.lazarus.personalojtdtr.ui

import android.content.Context
import android.net.Uri
import com.lazarus.personalojtdtr.data.backup.*
import kotlinx.coroutines.Job
import com.lazarus.personalojtdtr.domain.usecase.AttendanceRecovery
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.domain.usecase.DailyAttendance
import com.lazarus.personalojtdtr.util.AttendanceSharing
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OjtState(
    val profile: PersonalProfile = PersonalProfile(),
    val sessions: List<AttendanceSession> = emptyList(),
    val activities: List<DailyActivity> = emptyList(),
    val events: List<AttendanceEvent> = emptyList(),
    val legacySessions: List<AttendanceSession> = emptyList(),
    val resolutions: List<SessionResolution> = emptyList(),
    val loaded: Boolean = false,
    val loadError: String? = null,
)
data class CaptureUi(val draft: CaptureDraft, val working: Boolean = false, val error: String? = null)

class OjtViewModel(private val repository: OjtRepository, private val recorder: AttendanceRecorder, private val backup: OfflineBackupService) : ViewModel() {
    val state = combine(repository.profile, repository.sessions, repository.activities, repository.events, repository.resolutions) { p, s, a, e, r ->
        OjtState(profile = p, sessions = s + DailyAttendance.sessions(e, r), activities = a, events = e, legacySessions = s, resolutions = r, loaded = true)
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(OjtState(loadError = "Local data could not be loaded: ${error.message}"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OjtState())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun dismissMessage() { _message.value = null }

    private fun perform(success: String, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
                onSuccess()
                _message.value = success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "The change could not be saved. Please try again."
            } finally { _busy.value = false }
        }
    }
    val photos get() = recorder.photos
    private val _capture = MutableStateFlow<CaptureUi?>(null)
    val capture = _capture.asStateFlow()
    private val _confirmation = MutableStateFlow<AttendanceEvent?>(null)
    val confirmation = _confirmation.asStateFlow()
    fun dismissConfirmation() { _confirmation.value = null }

    fun openCapture(date: LocalDate, type: AttendanceEventType) {
        if (_busy.value || _capture.value != null) return
        _busy.value = true
        viewModelScope.launch {
            try { _capture.value = CaptureUi(recorder.newDraft(date, type)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Camera storage could not be opened." }
            finally { _busy.value = false }
        }
    }
    fun captureStarted(): Boolean {
        val current = _capture.value ?: return false
        if (current.working) return false
        _capture.value = current.copy(working = true, error = null)
        return true
    }
    fun captureSaved(id: String) {
        val current = _capture.value?.takeIf { it.draft.id == id } ?: return
        viewModelScope.launch {
            try { _capture.value = CaptureUi(recorder.captured(current.draft)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                runCatching { recorder.retake(current.draft) }
                _capture.value = current.copy(working = false, error = "Photo cannot be read. Capture again: ${e.message}")
            }
        }
    }
    fun captureFailed(id: String, message: String) {
        val current = _capture.value?.takeIf { it.draft.id == id } ?: return
        viewModelScope.launch {
            runCatching { recorder.retake(current.draft) }
            _capture.value = CaptureUi(current.draft.copy(capturedAt = null), error = "Capture failed: $message. Please try again.")
        }
    }
    fun retake() {
        val current = _capture.value ?: return
        if (current.working) return
        _capture.value = current.copy(working = true)
        viewModelScope.launch {
            try { _capture.value = CaptureUi(recorder.retake(current.draft)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _capture.value = current.copy(error = e.message) }
        }
    }
    fun cancelCapture() {
        val current = _capture.value ?: return
        if (current.working) return
        _capture.value = null
        viewModelScope.launch { recorder.cancel(current.draft) }
    }
    fun confirmCapture() {
        val current = _capture.value ?: return
        if (current.working || current.draft.capturedAt == null || _busy.value) return
        _capture.value = current.copy(working = true, error = null)
        _busy.value = true
        viewModelScope.launch {
            try {
                val saved = recorder.confirm(current.draft)
                _capture.value = null
                _confirmation.value = saved
                _message.value = "${saved.type.label} recorded"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _capture.value = current.copy(working = false, error = e.message ?: "Attendance could not be saved. Retake or retry.") }
            finally { _busy.value = false }
        }
    }
    fun share(context: Context, event: AttendanceEvent) {
        viewModelScope.launch {
            try { AttendanceSharing.share(context, photos, event) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "No application could share this photo." }
        }
    }
    fun resolve(date: LocalDate, session: OjtSession, kind: ResolutionKind) =
        perform("${session.label}: ${kind.label}") { repository.resolve(date, session, kind) }

    private val _restoreRevision = MutableStateFlow(0)
    val restoreRevision = _restoreRevision.asStateFlow()
    private val _backupUi = MutableStateFlow(BackupUi())
    val backupUi = _backupUi.asStateFlow()
    private var backupJob: Job? = null
    fun cancelBackup() { if (!_backupUi.value.applying) backupJob?.cancel() }
    fun exportBackup(destination: Uri) {
        if (_busy.value || _capture.value != null) return
        _busy.value = true
        _backupUi.value = BackupUi(label = "Preparing and verifying backup…")
        backupJob = viewModelScope.launch {
            try { backup.export(destination); _message.value = "Backup exported and verified. Store the unencrypted archive securely." }
            catch (e: CancellationException) { _message.value = "Export cancelled. If the document provider could not remove a partial file, delete that incomplete file."; throw e }
            catch (e: Exception) { _message.value = "Backup export failed. Check document access, free storage and referenced photos. No backup success was recorded. Delete any incomplete destination document before retrying." }
            finally { _backupUi.value = BackupUi(); _busy.value = false }
        }
    }
    fun inspectBackup(source: Uri) {
        if (_busy.value || _capture.value != null) return
        _busy.value = true
        _backupUi.value = BackupUi(label = "Validating backup in private staging…")
        backupJob = viewModelScope.launch {
            try { _backupUi.value = BackupUi(stage = backup.inspect(source)) }
            catch (e: CancellationException) { _backupUi.value = BackupUi(); _message.value = "Restore inspection cancelled. Current data was not replaced."; throw e }
            catch (e: Exception) { _backupUi.value = BackupUi(); _message.value = "Backup rejected: unsupported format, damaged/oversized archive, invalid records, or missing photos. Current records were not replaced." }
            finally { _busy.value = false }
        }
    }
    fun cancelRestorePreview() {
        val stage = _backupUi.value.stage ?: return
        if (_busy.value) return
        _backupUi.value = BackupUi()
        viewModelScope.launch { backup.cancel(stage) }
    }
    fun confirmRestore() {
        val stage = _backupUi.value.stage ?: return
        if (_busy.value || _capture.value != null) return
        _busy.value = true
        _backupUi.value = BackupUi(label = "Applying replacement safely…", applying = true)
        backupJob = viewModelScope.launch {
            try { backup.restore(stage); _backupUi.value = BackupUi(); _restoreRevision.value++; _message.value = "Backup restored. Local records and photo references were replaced." }
            catch (e: CancellationException) { throw e }
            catch (e: RestoreOutcomeUnknown) {
                backup.cancel(stage)
                _backupUi.value = BackupUi()
                _message.value = e.message
            }
            catch (e: Exception) { _backupUi.value = BackupUi(stage = stage, error = "Restore did not commit; current records are preserved. Check storage, then retry or cancel and select the backup again.") }
            finally { _busy.value = false }
        }
    }
    fun timeOut(breakMinutes: Long) = perform("Timed out") { repository.timeOut(breakMinutes) }
    fun saveProfile(profile: PersonalProfile) = perform("Profile saved") { repository.saveProfile(profile) }
    companion object {
        fun factory(repository: OjtRepository, recorder: AttendanceRecorder, backup: OfflineBackupService): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(OjtViewModel::class.java))
                return OjtViewModel(repository, recorder, backup) as T
            }
        }
    }
}

data class BackupUi(val label: String? = null, val applying: Boolean = false, val stage: StagedBackup? = null, val error: String? = null)
