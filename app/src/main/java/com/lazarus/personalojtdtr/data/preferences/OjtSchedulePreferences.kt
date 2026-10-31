package com.lazarus.personalojtdtr.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.ojtScheduleStore by preferencesDataStore(name = "ojt_schedule")

data class OjtSchedule(val hoursPerDay: Int? = null, val weekdays: Set<DayOfWeek> = emptySet())

class OjtSchedulePreferences(context: Context) {
    private val store = context.applicationContext.ojtScheduleStore
    val schedule = store.data.map { prefs ->
        val hours = prefs[HOURS]?.takeIf { it in 1..24 }
        val mask = prefs[DAYS] ?: 0
        OjtSchedule(hours, DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet())
    }
    suspend fun save(hoursPerDay: Int, weekdays: Set<DayOfWeek>) {
        require(hoursPerDay in 1..24 && weekdays.isNotEmpty())
        store.edit { prefs ->
            prefs[HOURS] = hoursPerDay
            prefs[DAYS] = weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
        }
    }
    private companion object {
        val HOURS = intPreferencesKey("expected_hours_per_day")
        val DAYS = intPreferencesKey("working_weekdays")
    }
}
