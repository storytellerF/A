package com.storyteller_f.shared.utils

import java.text.BreakIterator

actual fun checkContent(text: String): Result<Unit> {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\n\\p{IsEmoji}]+$"
    val breakIterator = BreakIterator.getCharacterInstance().apply {
        setText(text)
    }
    var start = breakIterator.first()
    var end = breakIterator.next()
    while (end != BreakIterator.DONE) {
        val sub = text.substring(start, end)
        if (!regexp.toRegex().matches(sub)) {
            return Result.failure(Exception("unsupported $sub"))
        }
        start = end
        end = breakIterator.next()
    }
    return UNIT_RESULT
}

actual fun safeFirstUnicode(text: String): String? {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\\p{IsEmoji}]"
    return regexp.toRegex().find(text)?.groupValues?.getOrNull(0)
}

actual fun safeFirstEmoji(text: String): String? {
    val regexp = "^\\p{IsEmoji}"
    return regexp.toRegex().find(text)?.groupValues?.getOrNull(0)
}
