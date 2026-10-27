package com.lazarus.personalojtdtr.ui.report

import android.net.Uri
import androidx.lifecycle.*
import com.lazarus.personalojtdtr.data.report.*
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.YearMonth

data class DtrExportState(val month: Int, val year: String, val report: DtrReport? = null,
    val file: File? = null, val busy: String? = null, val message: String? = null, val error: String? = null)

class DtrExportViewModel(private val repository: OjtRepository, private val service: DtrExportService) : ViewModel() {
    private val now = YearMonth.now(AttendanceCalculations.manila)
    private val _state = MutableStateFlow(DtrExportState(now.monthValue, now.year.toString()))
    val state = _state.asStateFlow()
    private var operation: Job? = null
    private var pendingSave: File? = null
    init { preview() }
    fun period(month: Int = _state.value.month, year: String = _state.value.year) {
        if (_state.value.busy != null) return
        _state.value = DtrExportState(month, year)
    }
    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.busy != null) return
        _state.value = _state.value.copy(busy = label, error = null, message = null)
        operation = viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { _state.value = _state.value.copy(message = "Export cancelled. Any incomplete destination document may need removal."); throw error }
            catch (_: Exception) { _state.value = _state.value.copy(error = "Export failed. Check free storage and document access, then retry. Any incomplete destination document may need removal.") }
            finally { _state.value = _state.value.copy(busy = null) }
        }
    }
    fun preview() {
        val s = _state.value
        val year = s.year.toIntOrNull()
        if (year == null || year !in 1800..2400) { _state.value = s.copy(error = "Enter a year from 1800 to 2400."); return }
        run("Loading report preview…") {
            val report = withContext(Dispatchers.IO) { repository.dtrReport(YearMonth.of(year, s.month)) }
            _state.value = _state.value.copy(report = report, file = null)
        }
    }
    fun generate() {
        val report = _state.value.report ?: return
        if (report.days == 0) return
        run("Generating PDF…") {
            val file = service.generate(report)
            _state.value = _state.value.copy(file = file, message = "PDF generated. Ready to share or save.")
        }
    }
    fun beginSave(): String? {
        val s = _state.value
        if (s.busy != null || s.file == null || s.report == null) return null
        pendingSave = s.file
        _state.value = s.copy(busy = "Choose a save location…", error = null, message = null)
        return "DTR-${s.report.period}.pdf"
    }
    fun saveLaunchFailed() {
        pendingSave = null
        _state.value = _state.value.copy(busy = null, error = "The Android document picker could not be opened. Retry or share the PDF.")
    }
    fun saveResult(destination: Uri?) {
        val file = pendingSave
        pendingSave = null
        _state.value = _state.value.copy(busy = null)
        if (destination == null) { _state.value = _state.value.copy(message = "Save cancelled."); return }
        if (file == null) { _state.value = _state.value.copy(error = "Export was interrupted. Generate the PDF again."); return }
        run("Saving and verifying PDF…") {
            service.save(file, destination)
            _state.value = _state.value.copy(message = "PDF saved and verified.")
        }
    }
    fun share() {
        val s = _state.value
        if (s.busy != null) return
        val file = s.file ?: return
        try { service.share(file); _state.value = s.copy(message = "Choose a destination in the Android Sharesheet.", error = null) }
        catch (_: Exception) { _state.value = s.copy(error = "PDF could not be shared. Generate it again or check available sharing apps.") }
    }
    fun cancel() { if (pendingSave == null) operation?.cancel() }
    companion object {
        fun factory(repository: OjtRepository, service: DtrExportService): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DtrExportViewModel::class.java))
                return DtrExportViewModel(repository, service) as T
            }
        }
    }
}
