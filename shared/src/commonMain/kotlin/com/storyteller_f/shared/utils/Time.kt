package com.storyteller_f.shared.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun now(): LocalDateTime {
    return Clock.System.now().toLocalDateTime(TimeZone.UTC)
}

@OptIn(ExperimentalTime::class)
fun nowInstance() = Clock.System.now()

@ExperimentalTime
fun LocalDateTime.formatTime(): String {
    val toLocalDateTime = toInstant(TimeZone.UTC)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalDateTime.Format {
        year()
        char('/')
        monthNumber()
        char('/')
        day(padding = Padding.ZERO)
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }.format(toLocalDateTime)
}

/**
 * 检查指定时间戳是否在指定范围内
 */
@ExperimentalTime
fun checkTsIsValid(currentStamp: Long, offset: Int): Pair<Long, Boolean> {
    val nowSeconds = now().toInstant(UtcOffset.ZERO).epochSeconds
    val isValid = currentStamp + offset > nowSeconds && currentStamp < nowSeconds
    return Pair(nowSeconds, isValid)
}
