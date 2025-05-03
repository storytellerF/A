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

/**
 * 检查指定时间戳是否在指定范围内
 */
fun checkTsIsValid(currentStamp: Long, offset: Int): Pair<Long, Boolean> {
    val nowSeconds = now().toInstant(UtcOffset.ZERO).epochSeconds
    val isValid = currentStamp + offset > nowSeconds && currentStamp < nowSeconds
    return Pair(nowSeconds, isValid)
}
