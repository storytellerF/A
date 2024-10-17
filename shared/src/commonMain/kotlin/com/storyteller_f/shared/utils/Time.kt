package com.storyteller_f.shared.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun now(): LocalDateTime {
    return Clock.System.now().toLocalDateTime(TimeZone.UTC)
}
