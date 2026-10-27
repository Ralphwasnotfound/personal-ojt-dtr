package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.AttendanceEvent

/** Files are durable before INSERT; only a verified rollback permits cleanup.
 * A process crash is recovered by reconciling managed directories with committed DB paths. */
interface ConfirmationFiles {
    fun prepare(event: AttendanceEvent)
    fun discardPrepared(event: AttendanceEvent)
    fun discardDraft(id: String)
}
class PhotoConfirmation(private val files: ConfirmationFiles) {
    suspend fun save(event: AttendanceEvent, committed: suspend (AttendanceEvent) -> Boolean,
        insert: suspend (AttendanceEvent) -> Unit): AttendanceEvent {
        files.prepare(event) // prepare owns/cleans partial files and refuses pre-existing destinations.
        try {
            insert(event)
        } catch (error: Throwable) {
            // A transaction can commit before its result is delivered. Never delete evidence
            // until a fresh database read establishes that this attempt did not commit.
            val saved = try { committed(event) } catch (verification: Throwable) {
                throw IllegalStateException("Save outcome is unknown. Reopen attendance to check your records; photo files were retained.", verification)
            }
            if (!saved) {
                runCatching { files.discardPrepared(event) }
                throw error
            }
        }
        // A cleanup problem after commit must never report attendance as failed or delete committed photos.
        runCatching { files.discardDraft(event.id) }
        return event
    }
}
