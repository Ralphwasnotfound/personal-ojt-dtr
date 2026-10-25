package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.DailyActivity
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ActivityRulesTest {
    private val day = LocalDate.of(2026, 10, 20)
    @Test fun `past dates and five ordered optional photos are valid`() { ActivityRules.validate("Network setup", "Configured a switch", day, listOf("5", "4", "3", "2", "1")); ActivityRules.validate("Learning", "Studied", day, emptyList()) }
    @Test(expected = IllegalArgumentException::class) fun `blank title rejected`() { ActivityRules.validate("  ", "Description", day, emptyList()) }
    @Test(expected = IllegalArgumentException::class) fun `blank description rejected`() { ActivityRules.validate("Title", "  ", day, emptyList()) }
    @Test(expected = IllegalArgumentException::class) fun `six photos rejected`() { ActivityRules.validate("Title", "Description", day, (1..6).map { "$it" }) }
    @Test(expected = IllegalArgumentException::class) fun `duplicate photo references rejected`() { ActivityRules.validate("Title", "Description", day, listOf("same", "same")) }
    @Test fun `new create and expected revision edit valid`() { ActivityRules.requireRevision(null, null); ActivityRules.requireRevision(2, 2) }
    @Test(expected = IllegalArgumentException::class) fun `repeated create cannot overwrite committed record`() { ActivityRules.requireRevision(null, 0) }
    @Test(expected = IllegalArgumentException::class) fun `repeated edit rejected after first save increments revision`() { ActivityRules.requireRevision(1, 2) }
    @Test(expected = IllegalArgumentException::class) fun `editing deleted activity rejected`() { ActivityRules.requireRevision(1, null) }
    @Test(expected = IllegalArgumentException::class) fun `retained attachment must belong to edited record`() { ActivityRules.requireOwnership(listOf("other-record-photo"), listOf("owned-photo")) }
    @Test fun `attachment removal and reordering do not change ownership`() { ActivityRules.requireOwnership(listOf("c", "a"), listOf("a", "b", "c")) }
    @Test fun `sort newest date then selected time then creation timestamp`() {
        val earlier = DailyActivity("earlier", day, "d", title = "Earlier", time = LocalTime.of(8, 0))
        val later = earlier.copy(id = "later", time = LocalTime.of(15, 0))
        val newestDay = earlier.copy(id = "newday", date = day.plusDays(1))
        assertEquals(listOf("newday", "later", "earlier"), ActivityRules.sorted(listOf(earlier, newestDay, later)).map { it.id })
    }
    @Test fun `date and optional local time format consistently`() {
        assertEquals("Oct 20, 2026", ActivityRules.dateLabel(day))
        assertEquals("12:05 AM", ActivityRules.timeLabel(LocalTime.of(0, 5)))
        assertEquals("1:30 PM", ActivityRules.timeLabel(LocalTime.of(13, 30)))
        val text = ActivityRules.documentation(DailyActivity("id", day, "Details", title = "Title", time = LocalTime.of(13, 30)))
        assertTrue(text.startsWith("Title\nOct 20, 2026 • 1:30 PM PH Time")); assertTrue(text.endsWith("Details"))
    }
    @Test fun `legacy descriptions receive nonempty title without changing description`() {
        assertEquals("First line Second", ActivityRules.legacyTitle("  First line\nSecond  "))
        assertEquals("Activity", ActivityRules.legacyTitle("\n "))
    }
}
