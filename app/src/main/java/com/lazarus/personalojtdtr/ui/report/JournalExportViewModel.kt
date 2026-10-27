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

data class JournalExportState(val month: Int, val year: String, val custom: Boolean = false, val start: String = "", val end: String = "", val report: JournalReport? = null,
    val file: File? = null, val busy: String? = null, val message: String? = null, val error: String? = null)

class JournalExportViewModel(private val repository: OjtRepository, private val service: JournalExportService) : ViewModel() {
    private val now = YearMonth.now(AttendanceCalculations.manila)
    private val _state = MutableStateFlow(JournalExportState(now.monthValue, now.year.toString(), start = now.atDay(1).toString(), end = now.atEndOfMonth().toString()))
    val state = _state.asStateFlow()
    private var operation: Job? = null
    private var pendingSave: File? = null
    fun period(month: Int = _state.value.month, year: String = _state.value.year,
        custom: Boolean = _state.value.custom, start: String = _state.value.start, end: String = _state.value.end) {
        if (_state.value.busy != null) return
        _state.value = JournalExportState(month, year, custom, start, end)
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
        if (s.busy != null) return
        val period = try {
            if (s.custom) JournalPeriod(java.time.LocalDate.parse(s.start), java.time.LocalDate.parse(s.end))
            else JournalPeriod.month(YearMonth.of(s.year.toInt(), s.month))
        } catch (_: Exception) {
            _state.value = s.copy(error = "Enter valid dates (YYYY-MM-DD), from 1800 to 2400. End must be on or after start."); return
        }
        run("Loading report preview…") {
            val report = withContext(Dispatchers.IO) { service.inspect(repository.journalReport(period)) }
            _state.value = _state.value.copy(report = report, file = null)
        }
    }
    fun generate() {
        val report = _state.value.report ?: return
        if (report.activities.isEmpty()) return
        run("Generating PDF…") {
            val (file, warnings) = service.generate(report)
            _state.value = _state.value.copy(file = file, report = report.copy(warnings = warnings), message = "PDF generated. Ready to share or save. ${warnings.size} unavailable photographs.")
        }
    }
    fun beginSave(): String? {
        val s = _state.value
        if (s.busy != null || s.file == null || s.report == null) return null
        pendingSave = s.file
        _state.value = s.copy(busy = "Choose a save location…", error = null, message = null)
        return s.report.period.filename
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
        try { service.share(file, s.report?.period?.filename ?: "OJT_Journal.pdf"); _state.value = s.copy(message = "Choose a destination in the Android Sharesheet.", error = null) }
        catch (_: Exception) { _state.value = s.copy(error = "PDF could not be shared. Generate it again or check available sharing apps.") }
    }
    fun cancel() { if (pendingSave == null) operation?.cancel() }
    companion object {
        fun factory(repository: OjtRepository, service: JournalExportService): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(JournalExportViewModel::class.java))
                return JournalExportViewModel(repository, service) as T
            }
        }
    }
}
