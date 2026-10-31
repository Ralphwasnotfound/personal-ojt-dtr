package com.lazarus.personalojtdtr.data.backup

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/** Strict, bounded JSON: duplicate keys, unknown/missing fields, floats and excessive depth are rejected. */
object BackupJson {
    private val gson = GsonBuilder().serializeNulls().create()
    fun encode(data: BackupRecords): String = JsonObject().apply {
        addProperty("format", "my-ojt-companion"); addProperty("version", 5); addProperty("schemaVersion", 6)
        addProperty("applicationId", "com.lazarus.personalojtdtr"); add("records", gson.toJsonTree(data))
    }.toString()

    fun decode(text: String): BackupRecords {
        var nodes = 0
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            fun read(depth: Int): JsonElement {
                require(depth <= 16 && ++nodes <= 250000) { "Backup manifest is too complex." }
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val name = reader.nextName(); require(!has(name)) { "Duplicate JSON field." }; add(name, read(depth + 1))
                        }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> JsonArray().apply { reader.beginArray(); while (reader.hasNext()) add(read(depth + 1)); reader.endArray() }
                    JsonToken.STRING -> JsonPrimitive(reader.nextString().also { require(it.length <= 20000) { "Manifest string too long." } })
                    JsonToken.NUMBER -> {
                        val value = reader.nextString(); require(value.matches(Regex("-?(0|[1-9][0-9]*)")) && value.toLongOrNull() != null) { "Expected a bounded integer." }; JsonPrimitive(value.toLong())
                    }
                    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                    JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
                    else -> error("Invalid backup JSON.")
                }
            }
            val root = read(0).asJsonObject
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Extra JSON content." }
            root.fields("format", "version", "schemaVersion", "applicationId", "records")
            val version = root.long("version")
            require(root.string("format") == "my-ojt-companion" && ((version == 1L && root.long("schemaVersion") == 3L) || (version == 2L && root.long("schemaVersion") == 4L) || (version == 3L && root.long("schemaVersion") == 5L) || (version == 4L && root.long("schemaVersion") == 6L) || (version == 5L && root.long("schemaVersion") == 6L)) && root.string("applicationId") == "com.lazarus.personalojtdtr") { "Unsupported backup format/version." }
            val r = root.getAsJsonObject("records")
            if (version >= 5L) r.fields("createdAt", "profile", "events", "legacy", "activities", "resolutions", "assets", "schedule", "calendarOverrides", "avatar")
            else if (version >= 3L) r.fields("createdAt", "profile", "events", "legacy", "activities", "resolutions", "assets", "schedule", "calendarOverrides")
            else r.fields("createdAt", "profile", "events", "legacy", "activities", "resolutions", "assets")
            val profile = if (r["profile"].isJsonNull) null else r.getAsJsonObject("profile").let {
                it.fields("id", "displayName", "requiredHours", "startDate", "defaultBreakSeconds")
                BackupProfile(it.int("id"), it.string("displayName"), it.long("requiredHours"), it.optionalLong("startDate"), it.long("defaultBreakSeconds"))
            }
            val events = r.rows("events").map {
                it.fields("id", "date", "type", "at", "capturedAt", "original", "stamped", "createdAt")
                BackupEvent(it.string("id"), it.long("date"), it.int("type"), it.long("at"), it.long("capturedAt"), it.string("original"), it.string("stamped"), it.long("createdAt"))
            }
            val legacy = r.rows("legacy").map {
                it.fields("id", "timeIn", "timeOut", "breakSeconds", "original", "outPhoto", "createdAt", "updatedAt")
                BackupLegacy(it.string("id"), it.long("timeIn"), it.optionalLong("timeOut"), it.long("breakSeconds"), it.optionalString("original"), it.optionalString("outPhoto"), it.long("createdAt"), it.long("updatedAt"))
            }
            val activities = r.rows("activities").map {
                if (version == 1L) it.fields("id", "date", "description", "photos", "createdAt", "updatedAt")
                else it.fields("id", "date", "description", "photos", "createdAt", "updatedAt", "title", "timeMinutes")
                val minutes = if (version == 1L || it["timeMinutes"].isJsonNull) null else it.int("timeMinutes")
                BackupActivity(it.string("id"), it.long("date"), it.string("description"), it.getAsJsonArray("photos").map { value -> require(value.isJsonPrimitive && value.asJsonPrimitive.isString); value.asString }, it.long("createdAt"), it.long("updatedAt"),
                    if (version == 1L) com.lazarus.personalojtdtr.domain.usecase.ActivityRules.legacyTitle(it.string("description")) else it.string("title"), minutes)
            }
            val resolutions = r.rows("resolutions").map {
                it.fields("id", "date", "session", "kind", "actedAt")
                BackupResolution(it.string("id"), it.long("date"), it.int("session"), it.int("kind"), it.long("actedAt"))
            }
            val assets = r.rows("assets").map {
                it.fields("id", "mediaType", "bytes", "sha256")
                BackupAsset(it.string("id"), it.string("mediaType"), it.long("bytes"), it.string("sha256"))
            }
            val schedule = if (version < 3L || r["schedule"].isJsonNull) null else r.getAsJsonObject("schedule").let {
                it.fields("expectedHoursPerDay", "workingWeekdaysMask")
                BackupSchedule(it.optionalInt("expectedHoursPerDay"), it.int("workingWeekdaysMask"))
            }
            val calendar = if (version < 3L) emptyList() else r.rows("calendarOverrides").map {
                if (version >= 4L) it.fields("date", "kind", "reason", "updatedAt", "scope") else it.fields("date", "kind", "reason", "updatedAt")
                BackupCalendarOverride(it.long("date"), it.int("kind"), it.optionalString("reason"), it.long("updatedAt"), if (version >= 4L) it.int("scope") else 2)
            }
            return BackupRecords(r.long("createdAt"), profile, events, legacy, activities, resolutions, assets, schedule, calendar, if (version >= 5L) r.optionalString("avatar") else null)
        }
    }
    private fun JsonObject.fields(vararg names: String) { require(keySet() == names.toSet()) { "Missing or unknown manifest fields." } }
    private fun JsonObject.string(key: String): String = get(key).let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Expected text field." }; it.asString }
    private fun JsonObject.long(key: String): Long = get(key).let { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "Expected integer field." }; it.asLong }
    private fun JsonObject.int(key: String): Int = long(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JsonObject.optionalLong(key: String): Long? = if (get(key).isJsonNull) null else long(key)
    private fun JsonObject.optionalString(key: String): String? = if (get(key).isJsonNull) null else string(key)
    private fun JsonObject.optionalInt(key: String): Int? = if (get(key).isJsonNull) null else int(key)
    private fun JsonObject.rows(key: String): List<JsonObject> = getAsJsonArray(key).also { require(it.size() <= 20000) { "Too many records." } }.map { it.asJsonObject }
}
