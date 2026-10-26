package com.lazarus.personalojtdtr.util

// Request a fresh read from Room; callers must never publish a cached attendance summary.
interface WidgetPublisher { fun refresh() }
