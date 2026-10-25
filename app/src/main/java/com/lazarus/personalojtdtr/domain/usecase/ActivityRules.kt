package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.DailyActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ActivityRules {
    const val MAX_PHOTOS = 5
    fun legacyTitle(description: String): String = description.replace('\n', ' ').trim().take(80).ifBlank { "Activity" }
    fun validate(title: String, description: String, date: LocalDate, photoPaths: List<String>) {
        require(title.trim().length in 1..120) { "Enter a title (1–120 characters)." }
        require(description.trim().length in 1..10000) { "Enter a description (1–10000 characters)." }
        require(date.year in 1800..2400) { "Choose an activity date between 1800 and 2400." }
        require(photoPaths.size <= MAX_PHOTOS && photoPaths.distinct().size == photoPaths.size && photoPaths.none { it.isBlank() }) { "Attach up to five distinct photos." }
    }
    fun requireRevision(expected: Long?, current: Long?) {
        if (expected == null) require(current == null) { "This activity was already saved. Reopen it to edit." }
        else require(current == expected) { "This activity changed or was deleted. Reopen it before editing." }
    }
    fun requireOwnership(retained: List<String>, existing: List<String>) {
        require(retained.all { it in existing }) { "A photo does not belong to this activity." }
    }
    fun sorted(activities: List<DailyActivity>): List<DailyActivity> = activities.sortedWith(
        compareByDescending<DailyActivity> { it.date }.thenByDescending { it.time?.toSecondOfDay() ?: -1 }.thenByDescending { it.createdAt }.thenBy { it.id })
    fun dateLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
    fun timeLabel(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    fun documentation(activity: DailyActivity): String = "${activity.title}\n${dateLabel(activity.date)}${activity.time?.let { " • ${timeLabel(it)} PH Time" }.orEmpty()}\n\n${activity.description}"
}
