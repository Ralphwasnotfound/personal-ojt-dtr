package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.AttendanceEvent

/** Files are durable before INSERT; failed INSERT removes only this attempt's files.
 * A process crash is recovered by reconciling managed directories with committed DB paths. */
interface ConfirmationFiles {
    fun prepare(event: AttendanceEvent)
    fun discardPrepared(event: AttendanceEvent)
    fun discardDraft(id: String)
}
class PhotoConfirmation(private val files: ConfirmationFiles) {
    suspend fun save(event: AttendanceEvent, insert: suspend (AttendanceEvent) -> Unit): AttendanceEvent {
        files.prepare(event) // prepare owns/cleans partial files and refuses pre-existing destinations.
        try {
            insert(event)
        } catch (error: Throwable) {
            // Best-effort cleanup must never replace the original validation/storage error.
            runCatching { files.discardPrepared(event) }
            throw error
        }
        // A cleanup problem after commit must never report attendance as failed or delete committed photos.
        runCatching { files.discardDraft(event.id) }
        return event
    }
}
