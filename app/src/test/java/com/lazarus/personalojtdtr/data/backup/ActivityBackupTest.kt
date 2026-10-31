package com.lazarus.personalojtdtr.data.backup

import com.google.gson.JsonParser
import com.lazarus.personalojtdtr.domain.usecase.ActivityRules
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActivityBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private val now = Instant.parse("2026-10-24T13:00:00Z").toEpochMilli()
    private fun records() = BackupRecords(now, null, emptyList(), emptyList(), listOf(BackupActivity("old-id", 20000, "Configured switch ports", emptyList(), now - 1000, now, "Network setup", 870)), emptyList(), emptyList())
    @Test fun `format three includes title optional time schedule and calendar defaults`() {
        val json = BackupJson.encode(records())
        assertEquals(5, JsonParser.parseString(json).asJsonObject["version"].asInt)
        assertEquals(6, JsonParser.parseString(json).asJsonObject["schemaVersion"].asInt)
        assertEquals(records(), BackupJson.decode(json))
    }
    @Test fun `P3 format one remains readable with derived title and no fabricated time`() {
        val root = JsonParser.parseString(BackupJson.encode(records())).asJsonObject
        root["records"].asJsonObject.remove("avatar")
        root.addProperty("version", 1); root.addProperty("schemaVersion", 3)
        root["records"].asJsonObject.remove("schedule"); root["records"].asJsonObject.remove("calendarOverrides")
        root["records"].asJsonObject["activities"].asJsonArray.forEach { it.asJsonObject.remove("title"); it.asJsonObject.remove("timeMinutes") }
        val read = BackupJson.decode(root.toString()).activities.single()
        assertEquals(ActivityRules.legacyTitle(read.description), read.title); assertNull(read.timeMinutes)
        assertEquals(records().activities.single().createdAt, read.createdAt); assertEquals(records().activities.single().updatedAt, read.updatedAt)
    }
    @Test fun `P4 format two remains readable with empty schedule defaults`() {
        val root = JsonParser.parseString(BackupJson.encode(records())).asJsonObject
        root["records"].asJsonObject.remove("avatar")
        root.addProperty("version", 2); root.addProperty("schemaVersion", 4)
        root["records"].asJsonObject.remove("schedule"); root["records"].asJsonObject.remove("calendarOverrides")
        val read = BackupJson.decode(root.toString())
        assertNull(read.schedule); assertTrue(read.calendarOverrides.isEmpty())
    }
    @Test fun `format three preserves schedule and special date overrides`() {
        val data = records().copy(schedule = BackupSchedule(8, (1 shl 0) or (1 shl 4)),
            calendarOverrides = listOf(BackupCalendarOverride(20686, 0, "Campus holiday", now - 1000)))
        BackupValidation.validate(data)
        assertEquals(data, BackupJson.decode(BackupJson.encode(data)))
    }
    @Test(expected = IllegalArgumentException::class) fun `mismatched format schema pair rejected`() { BackupJson.decode(BackupJson.encode(records()).replace("\"schemaVersion\":6", "\"schemaVersion\":4")) }
    @Test(expected = IllegalArgumentException::class) fun `blank restored activity title rejected`() { BackupValidation.validate(records().copy(activities = records().activities.map { it.copy(title = " ") })) }
    @Test(expected = IllegalArgumentException::class) fun `invalid restored optional time rejected`() { BackupValidation.validate(records().copy(activities = records().activities.map { it.copy(timeMinutes = 1440) })) }
    @Test fun `photo references and ordering survive JSON serialization`() {
        val data = records().copy(activities = records().activities.map { it.copy(photos = listOf("third", "first", "second")) })
        assertEquals(data, BackupJson.decode(BackupJson.encode(data)))
    }
    @Test fun `all five activity photos included and remapped safely during replace`() = runBlocking {
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAC0lEQVR4XmNgQAYAAA4AAa7vfYAAAAAASUVORK5CYII=")
        val ids = List(5) { UUID.randomUUID().toString() }
        val data = records().copy(activities = records().activities.map { it.copy(photos = ids) })
        val sources = ids.associateWith { temp.newFile().apply { writeBytes(png) } }
        val archive = temp.newFile()
        val written = BackupArchive.create(archive, data, sources, { require(BackupArchive.mediaType(it) == "image/png") })
        val stage = BackupArchive.inspect(archive, temp.root, { require(BackupArchive.mediaType(it) == "image/png") })
        assertEquals(5, stage.records.assets.size); assertEquals(written, stage.records)
        var restored: List<String> = emptyList()
        val target = object : RestoreTarget {
            override suspend fun replace(records: BackupRecords, paths: Map<String, String>, generation: String) { restored = records.activities.single().photos.map(paths::getValue) }
            override suspend fun applied(generation: String) = false
        }
        val root = temp.newFolder()
        val generation = AtomicRestore(root, target).apply(stage) { require(BackupArchive.mediaType(it) == "image/png") }
        assertEquals(ids.map { "restore-generations/$generation/assets/$it.png" }, restored)
        assertTrue(restored.all { File(root, it).isFile }); assertTrue(archive.isFile)
    }

    @Test fun `all workday classifications survive backup round trip`() {
        val data = records().copy(calendarOverrides = (0..5).map { BackupCalendarOverride(20686L + it, it, "Reason notes", now - 1000) })
        BackupValidation.validate(data)
        assertEquals(data, BackupJson.decode(BackupJson.encode(data)))
    }

    @Test fun `schema five backup defaults existing overrides to whole day`() {
        val data = records().copy(calendarOverrides = listOf(BackupCalendarOverride(20686, 4, "Holiday", now - 1000)))
        val root = JsonParser.parseString(BackupJson.encode(data)).asJsonObject
        root["records"].asJsonObject.remove("avatar")
        root.addProperty("version", 3); root.addProperty("schemaVersion", 5)
        root["records"].asJsonObject["calendarOverrides"].asJsonArray.forEach { it.asJsonObject.remove("scope") }
        assertEquals(data, BackupJson.decode(root.toString()))
    }
    @Test fun `scoped exceptions preserve valid afternoon only attendance in backup replay`() {
        val day = java.time.LocalDate.parse("2026-11-23")
        val start = day.atTime(13, 0).atZone(java.time.ZoneId.of("Asia/Manila")).toInstant().toEpochMilli()
        val ids = List(4) { java.util.UUID.randomUUID().toString() }
        val data = BackupRecords(start + 7200000, null, listOf(
            BackupEvent("in", day.toEpochDay(), 2, start, start, ids[0], ids[1], start),
            BackupEvent("out", day.toEpochDay(), 3, start + 3600000, start + 3600000, ids[2], ids[3], start + 3600000)),
            emptyList(), emptyList(), emptyList(), ids.map { BackupAsset(it, "image/jpeg", 1, "0".repeat(64)) },
            calendarOverrides = listOf(BackupCalendarOverride(day.toEpochDay(), 3, "Morning leave", start - 3600000, 0),
                BackupCalendarOverride(day.plusDays(1).toEpochDay(), 2, null, start - 3600000, 1)))
        BackupValidation.validate(data)
        assertEquals(data, BackupJson.decode(BackupJson.encode(data)))
    }
}
