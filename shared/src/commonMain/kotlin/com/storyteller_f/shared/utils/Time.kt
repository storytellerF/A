package com.storyteller_f.shared.utils

import kotlinx.datetime.*
import kotlinx.datetime.format.char

fun now(): LocalDateTime {
    return Clock.System.now().toLocalDateTime(TimeZone.UTC)
}

fun LocalDateTime.formatTime(): String {
    val toLocalDateTime = toInstant(TimeZone.UTC)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalDateTime.Format {
        year()
        char('/')
        monthNumber()
        char('/')
        dayOfMonth()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }.format(toLocalDateTime)
}
