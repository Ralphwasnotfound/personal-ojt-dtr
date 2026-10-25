package com.lazarus.personalojtdtr.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray

class Converters {
    @TypeConverter fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun dateToDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun dayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun pathsToJson(value: List<String>): String = JSONArray(value).toString()
    @TypeConverter fun jsonToPaths(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { array.getString(it) }
    }
}
