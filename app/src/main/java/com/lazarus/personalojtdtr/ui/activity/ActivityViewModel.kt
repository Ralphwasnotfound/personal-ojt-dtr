package com.lazarus.personalojtdtr.ui.activity

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.lazarus.personalojtdtr.data.photos.ActivityAttachment
import com.lazarus.personalojtdtr.data.repository.ActivityJournal
import com.lazarus.personalojtdtr.domain.model.DailyActivity
import com.lazarus.personalojtdtr.domain.usecase.ActivityRules
import com.lazarus.personalojtdtr.util.today
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ActivityEditor(val id: String, val draftId: String, val expectedRevision: Long?, val title: String,
    val description: String, val date: String, val time: String, val photos: List<ActivityAttachment>, val error: String? = null)
data class ActivityCamera(val id: String, val file: File, val photo: ActivityAttachment? = null, val working: Boolean = false, val error: String? = null)

class ActivityViewModel(private val journal: ActivityJournal, private val saved: SavedStateHandle) : ViewModel() {
    val photos get() = journal.photos
    private val _editor = MutableStateFlow(readEditor(saved["activity-editor"]))
    val editor = _editor.asStateFlow()
    private val _camera = MutableStateFlow<ActivityCamera?>(null)
    val camera = _camera.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    fun dismissError() { _operationError.value = null }
    val selectedId = saved.getStateFlow<String?>("activity-detail", null)
    fun dismissMessage() { _message.value = null }
    fun detail(id: String?) { saved["activity-detail"] = id }
    private fun setEditor(value: ActivityEditor?) {
        _editor.value = value
        saved["activity-editor"] = value?.let { e -> Bundle().apply {
            putString("id", e.id); putString("draft", e.draftId); putLong("revision", e.expectedRevision ?: -1)
            putString("title", e.title); putString("description", e.description); putString("date", e.date); putString("time", e.time)
            putStringArrayList("paths", ArrayList(e.photos.map { it.path })); putBooleanArray("pending", e.photos.map { it.pending }.toBooleanArray())
        } }
    }
    fun edit(activity: DailyActivity? = null) {
        if (_busy.value || _editor.value != null) return
        setEditor(ActivityEditor(activity?.id ?: UUID.randomUUID().toString(), UUID.randomUUID().toString(), activity?.revision,
            activity?.title.orEmpty(), activity?.description.orEmpty(), (activity?.date ?: today()).toString(), activity?.time?.toString().orEmpty(), activity?.photoPaths.orEmpty().map { ActivityAttachment(it, false) }))
    }
    fun fields(title: String? = null, description: String? = null, date: String? = null, time: String? = null) {
        if (_busy.value) return
        _editor.value?.let { setEditor(it.copy(title = title ?: it.title, description = description ?: it.description, date = date ?: it.date, time = time ?: it.time, error = null)) }
    }
    fun move(index: Int, delta: Int) {
        val e = _editor.value ?: return
        if (_busy.value || index !in e.photos.indices || index + delta !in e.photos.indices) return
        val reordered = e.photos.toMutableList().apply { val item = removeAt(index); add(index + delta, item) }
        setEditor(e.copy(photos = reordered, error = null))
    }
    fun remove(index: Int) {
        val e = _editor.value ?: return
        if (_busy.value || index !in e.photos.indices) return
        val photo = e.photos[index]
        setEditor(e.copy(photos = e.photos.filterIndexed { i, _ -> i != index }, error = null))
        viewModelScope.launch { runCatching { journal.removePending(e.draftId, photo) } }
    }
    fun importPhotos(uris: List<Uri>) {
        val e = _editor.value ?: return
        if (_busy.value || uris.isEmpty()) return
        if (e.photos.size + uris.size > ActivityRules.MAX_PHOTOS) { setEditor(e.copy(error = "Choose no more than ${5 - e.photos.size} additional photos.")); return }
        _busy.value = true
        viewModelScope.launch {
            try { setEditor(e.copy(photos = e.photos + journal.import(e.draftId, uris), error = null)) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { setEditor(e.copy(error = "Photo import failed. Use readable JPEG, PNG or WebP images up to 20 MiB each.")) }
            finally { _busy.value = false }
        }
    }
    fun openCamera() {
        val e = _editor.value ?: return
        if (_busy.value || e.photos.size >= 5 || _camera.value != null) return
        _busy.value = true
        viewModelScope.launch {
            try { journal.startup(); _camera.value = ActivityCamera(UUID.randomUUID().toString(), withContext(Dispatchers.IO) { photos.newCapture(e.draftId) }) }
            catch (_: Exception) { setEditor(e.copy(error = "Camera storage could not be opened.")) }
            finally { _busy.value = false }
        }
    }
    fun captureStarted(): Boolean {
        val c = _camera.value ?: return false
        if (c.working) return false
        _camera.value = c.copy(working = true, error = null); return true
    }
    fun captured(id: String) {
        val c = _camera.value?.takeIf { it.id == id } ?: return
        val e = _editor.value ?: return
        viewModelScope.launch {
            try { _camera.value = c.copy(photo = journal.acceptCapture(e.draftId, c.file), working = false, error = null) }
            catch (_: Exception) { _camera.value = c.copy(working = false, error = "Photo is unreadable. Retake or cancel.") }
        }
    }
    fun captureFailed(id: String) { _camera.value?.takeIf { it.id == id }?.let { _camera.value = it.copy(working = false, error = "Capture failed. Retry or cancel.") } }
    fun retake() {
        val c = _camera.value ?: return; val e = _editor.value ?: return
        if (c.working) return
        _camera.value = c.copy(working = true)
        viewModelScope.launch {
            journal.removePending(e.draftId, ActivityAttachment(photos.relative(c.file), true))
            _camera.value = c.copy(photo = null, working = false, error = null)
        }
    }
    fun usePhoto() {
        val c = _camera.value ?: return; val e = _editor.value ?: return
        if (c.working || c.photo == null || e.photos.size >= 5) return
        setEditor(e.copy(photos = e.photos + c.photo, error = null)); _camera.value = null
    }
    fun cancelCamera() {
        val c = _camera.value ?: return; val e = _editor.value ?: return
        if (c.working) return
        _camera.value = null
        viewModelScope.launch { journal.removePending(e.draftId, ActivityAttachment(photos.relative(c.file), true)) }
    }
    fun cancelEditor() {
        val e = _editor.value ?: return
        if (_busy.value) return
        setEditor(null); _camera.value = null
        viewModelScope.launch { runCatching { journal.discard(e.draftId) } }
    }
    fun save() {
        val e = _editor.value ?: return
        if (_busy.value) return
        val date = runCatching { LocalDate.parse(e.date) }.getOrNull()
        val time = if (e.time.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) runCatching { LocalTime.parse(e.time) }.getOrNull() else null
        val invalid = runCatching { require(date != null) { "Choose a valid activity date." }; require(e.time.isBlank() || time != null) { "Use a valid time (HH:mm) or leave it blank." }; ActivityRules.validate(e.title, e.description, date, e.photos.map { it.path }) }.exceptionOrNull()
        if (invalid != null) { setEditor(e.copy(error = invalid.message)); return }
        _busy.value = true
        viewModelScope.launch {
            try {
                journal.save(e.id, e.expectedRevision, e.draftId, e.title, e.description, requireNotNull(date), time, e.photos)
                setEditor(null); detail(e.id); _message.value = "Activity saved"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { setEditor(e.copy(error = if (error is IllegalArgumentException || error is IllegalStateException) error.message else "Activity could not be saved. Check storage and try again.")) }
            finally { _busy.value = false }
        }
    }
    fun delete(activity: DailyActivity) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { journal.delete(activity.id, activity.revision); detail(null); _message.value = "Activity deleted" }
            catch (_: Exception) { _operationError.value = "Activity changed or could not be deleted. Reopen it and try again." }
            finally { _busy.value = false }
        }
    }
    fun restored() { if (!_busy.value) { cancelEditor(); detail(null); _operationError.value = null; _message.value = null } }
    companion object {
        private fun readEditor(bundle: Bundle?): ActivityEditor? = runCatching {
            bundle ?: return null
            val paths = bundle.getStringArrayList("paths").orEmpty(); val flags = bundle.getBooleanArray("pending") ?: BooleanArray(0)
            ActivityEditor(requireNotNull(bundle.getString("id")), requireNotNull(bundle.getString("draft")), bundle.getLong("revision").takeIf { it >= 0 }, bundle.getString("title").orEmpty(), bundle.getString("description").orEmpty(), bundle.getString("date").orEmpty(), bundle.getString("time").orEmpty(), paths.mapIndexed { index, path -> ActivityAttachment(path, flags.getOrElse(index) { false }) })
        }.getOrNull()
        fun factory(journal: ActivityJournal): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(ActivityViewModel::class.java))
                return ActivityViewModel(journal, extras.createSavedStateHandle()) as T
            }
        }
    }
}
