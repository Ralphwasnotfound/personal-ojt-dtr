package com.lazarus.personalojtdtr.util

import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Duration.hoursLabel(): String = "${toHours()}h ${toMinutes() % 60}m"
fun Instant.manilaLabel(): String = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm:ss a", Locale.ENGLISH)
    .withZone(AttendanceCalculations.manila).format(this)
fun today(): LocalDate = LocalDate.now(AttendanceCalculations.manila)
