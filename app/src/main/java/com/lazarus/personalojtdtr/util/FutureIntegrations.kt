package com.lazarus.personalojtdtr.util

import com.lazarus.personalojtdtr.domain.model.HourSummary

// Backup/restore now lives in data/backup; this is the remaining deferred integration.
interface WidgetPublisher { suspend fun refresh(summary: HourSummary, timedIn: Boolean) }
