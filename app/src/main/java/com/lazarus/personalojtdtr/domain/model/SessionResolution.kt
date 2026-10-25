package com.lazarus.personalojtdtr.domain.model

import java.time.Instant
import java.time.LocalDate

enum class OjtSession(val code: Int, val label: String) {
    MORNING(0, "Morning"), AFTERNOON(1, "Afternoon");
    val startCode get() = code * 2
}
enum class ResolutionKind(val code: Int, val label: String) {
    INCOMPLETE(0, "Incomplete / unverified"), SKIPPED(1, "Skipped / not worked")
}
data class SessionResolution(val id: String, val date: LocalDate, val session: OjtSession,
    val kind: ResolutionKind, val actedAt: Instant)
