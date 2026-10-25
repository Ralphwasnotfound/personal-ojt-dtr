package com.lazarus.personalojtdtr.data.backup

import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import java.time.*
import java.util.UUID

data class BackupProfile(val id: Int, val displayName: String, val requiredHours: Long, val startDate: Long?, val defaultBreakSeconds: Long)
data class BackupEvent(val id: String, val date: Long, val type: Int, val at: Long, val capturedAt: Long, val original: String, val stamped: String, val createdAt: Long)
data class BackupLegacy(val id: String, val timeIn: Long, val timeOut: Long?, val breakSeconds: Long, val original: String?, val outPhoto: String?, val createdAt: Long, val updatedAt: Long)
data class BackupActivity(val id: String, val date: Long, val description: String, val photos: List<String>, val createdAt: Long, val updatedAt: Long, val title: String = ActivityRules.legacyTitle(description), val timeMinutes: Int? = null)
data class BackupResolution(val id: String, val date: Long, val session: Int, val kind: Int, val actedAt: Long)
data class BackupAsset(val id: String, val mediaType: String, val bytes: Long, val sha256: String) {
    val entry: String get() = "assets/$id.${extension(mediaType)}"
    companion object {
        fun extension(type: String): String = when (type) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> error("Unsupported photo format.") }
    }
}
data class BackupRecords(val createdAt: Long, val profile: BackupProfile?, val events: List<BackupEvent>,
    val legacy: List<BackupLegacy>, val activities: List<BackupActivity>, val resolutions: List<BackupResolution>, val assets: List<BackupAsset>) {
    fun photoReferences(): List<String> = events.flatMap { listOf(it.original, it.stamped) } +
        legacy.flatMap { listOfNotNull(it.original, it.outPhoto) } + activities.flatMap { it.photos }
    fun preview() = BackupPreview(createdAt, events.size + legacy.size, activities.size, assets.size, profile?.displayName, profile?.requiredHours ?: 0, resolutions.size)
}
data class BackupPreview(val createdAt: Long, val attendanceCount: Int, val activityCount: Int, val photoCount: Int,
    val displayName: String?, val requiredHours: Long, val resolutionCount: Int)

object BackupValidation {
    private fun date(value: Long): LocalDate = LocalDate.ofEpochDay(value).also { require(it.year in 1800..2400) { "Date is outside supported backup bounds." } }
    private fun time(value: Long): Instant = Instant.ofEpochMilli(value).also { require(it.atZone(ZoneOffset.UTC).year in 1800..2400) { "Timestamp is outside supported backup bounds." } }
    private fun ids(values: List<String>) { require(values.size <= 20000 && values.distinct().size == values.size && values.all { it.isNotBlank() && it.length <= 100 }) { "Invalid or duplicate record IDs." } }
    fun validate(data: BackupRecords) {
        time(data.createdAt)
        data.profile?.let {
            require(it.id == 1 && it.displayName.length <= 100 && it.requiredHours in 0..100000 && it.defaultBreakSeconds in 0..86400) { "Invalid profile/settings." }
            it.startDate?.let(::date)
        }
        ids(data.events.map { it.id }); ids(data.legacy.map { it.id }); ids(data.activities.map { it.id }); ids(data.resolutions.map { it.id })
        require(data.assets.size <= 10000 && data.assets.map { it.id }.distinct().size == data.assets.size) { "Too many or duplicate photo assets." }
        data.assets.forEach {
            require(UUID.fromString(it.id).toString() == it.id && it.bytes in 1..BackupArchive.MAX_PHOTO_BYTES && it.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid photo asset metadata." }
            BackupAsset.extension(it.mediaType)
        }
        val refs = data.photoReferences()
        require(refs.toSet() == data.assets.map { it.id }.toSet()) { "Missing or unreferenced photo assets." }
        val legacy = data.legacy.map { s ->
            require(s.breakSeconds in 0..86400 && s.createdAt <= s.updatedAt) { "Invalid legacy attendance metadata." }
            time(s.createdAt); time(s.updatedAt)
            val start = time(s.timeIn); val end = s.timeOut?.let(::time)
            if (end != null) require(end > start && Duration.between(start, end) <= Duration.ofDays(366) && Duration.ofSeconds(s.breakSeconds) <= Duration.between(start, end)) { "Invalid legacy attendance range." }
            AttendanceSession(s.id, start, end, Duration.ofSeconds(s.breakSeconds), s.original, s.outPhoto)
        }
        legacy.forEach { AttendanceCalculations.requireNoOverlap(it, legacy) }
        data.activities.forEach {
            date(it.date); time(it.createdAt); time(it.updatedAt)
            ActivityRules.validate(it.title, it.description, LocalDate.ofEpochDay(it.date), it.photos)
            require(it.createdAt <= it.updatedAt && (it.timeMinutes == null || it.timeMinutes in 0..1439)) { "Invalid activity record." }
        }
        require(data.legacy.all { it.createdAt <= data.createdAt && it.updatedAt <= data.createdAt && it.timeIn <= data.createdAt && (it.timeOut == null || it.timeOut <= data.createdAt) } &&
            data.activities.all { it.createdAt <= data.createdAt && it.updatedAt <= data.createdAt }) { "Backup date precedes its local records." }
        val assetsById = data.assets.associateBy { it.id }
        val events = data.events.map {
            require(assetsById.getValue(it.original).mediaType == "image/jpeg" && assetsById.getValue(it.stamped).mediaType == "image/jpeg") { "Captured attendance originals and timestamped photos must be JPEG images." }
            require(it.type in 0..3 && it.createdAt == it.at) { "Invalid attendance event metadata." }
            AttendanceEvent(it.id, date(it.date), AttendanceEventType.fromCode(it.type), time(it.at), time(it.capturedAt), it.original, it.stamped)
        }
        val actions = data.resolutions.map {
            require(it.session in 0..1 && it.kind in 0..1) { "Invalid session resolution." }
            SessionResolution(it.id, date(it.date), OjtSession.entries[it.session], ResolutionKind.entries[it.kind], time(it.actedAt))
        }
        val history = (events.map { Triple(it.occurredAt, it, null as SessionResolution?) } + actions.map { Triple(it.actedAt, null as AttendanceEvent?, it) }).sortedBy { it.first }
        val replayEvents = mutableListOf<AttendanceEvent>(); val replayActions = mutableListOf<SessionResolution>()
        for ((_, event, action) in history) {
            if (event != null) { DailyAttendance.validate(event, replayEvents, legacy, replayActions); replayEvents += event }
            if (action != null) { AttendanceRecovery.validate(action, replayEvents, replayActions); replayActions += action }
        }
        require(history.all { it.first <= Instant.ofEpochMilli(data.createdAt) }) { "Backup date precedes its attendance history." }
    }
}
