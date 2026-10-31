package com.lazarus.personalojtdtr.reminders

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import com.lazarus.personalojtdtr.domain.model.OjtSession
import java.time.*

private val Context.reminderData by preferencesDataStore("attendance_reminders")
class ReminderPreferences(private val store: androidx.datastore.core.DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.reminderData)
    private val gson = Gson()
    private val config = stringPreferencesKey("settings")
    private val asked = booleanPreferencesKey("permission_requested")
    val settings = store.data.map { p -> p[config]?.let { runCatching { gson.fromJson(it, ReminderSettings::class.java) }.getOrNull() }?.takeIf { it.valid() } ?: ReminderSettings() }.distinctUntilChanged()
    val permissionAsked = store.data.map { it[asked] ?: false }
    suspend fun askedPermission() { store.edit { it[asked] = true } }
    suspend fun save(value: ReminderSettings) { require(value.valid()); store.edit { it[config] = gson.toJson(value) } }
    suspend fun fired(date: LocalDate, session: OjtSession, occurrence: ReminderOccurrence) = store.data.first()[longPreferencesKey("fired:${ReminderRules.key(date, session, occurrence)}")] != null
    suspend fun claim(date: LocalDate, session: OjtSession, occurrence: ReminderOccurrence, now: Instant): Boolean {
        var claimed = false
        store.edit { p ->
            val key = longPreferencesKey("fired:${ReminderRules.key(date, session, occurrence)}")
            val lastKey = longPreferencesKey("last:$date:${session.code}")
            val last = p[lastKey]
            val minimumGap = when (occurrence) { ReminderOccurrence.SECOND -> 15 * 60_000L; ReminderOccurrence.SNOOZE -> 5 * 60_000L; else -> 0L }
            if (p[key] == null && (last == null || now.toEpochMilli() - last >= minimumGap)) {
                p[key] = now.toEpochMilli(); if (occurrence != ReminderOccurrence.READY) p[lastKey] = now.toEpochMilli(); claimed = true
            }
            // Keep a bounded delivery ledger, including across process death and reboot.
            p.asMap().keys.filter { it.name.startsWith("fired:") || it.name.startsWith("last:") || it.name.startsWith("snoozed:") }.forEach { old ->
                val day = runCatching { LocalDate.parse(old.name.substringAfter(':').substringBefore(':')) }.getOrNull()
                if (day != null && day < date.minusDays(14)) p.remove(old)
            }
        }
        return claimed
    }
    suspend fun claimSnooze(date: LocalDate, session: OjtSession): Boolean {
        var claimed = false
        store.edit { p -> val key = booleanPreferencesKey("snoozed:$date:${session.code}"); if (p[key] != true) { p[key] = true; claimed = true } }
        return claimed
    }
}
