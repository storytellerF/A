package com.storyteller_f.shared.utils

import android.icu.text.BreakIterator
import android.icu.text.UnicodeSet

actual fun checkContent(text: String): Result<Unit> {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\n]+$"
    val emojiSet = UnicodeSet("[[:RGI_Emoji:]]").safeFreeze()
    val nerdFontSet = UnicodeSet(
        "[\\uE000-\\uE0FF \\uE5FA-\\uE6B7 \\uE700-\\uE8EF " +
            "\\uEA60-\\uEC1E \\uED00-\\uEFCE \\uF000-\\uF2FF " +
            "\\uF300-\\uF381 \\uF400-\\uFD46 " +
            "\\U000F0001-\\U000F1AF0]"
    ).safeFreeze()
    val breakIterator = BreakIterator.getCharacterInstance().apply {
        setText(text)
    }
    var start = breakIterator.first()
    var end = breakIterator.next()
    while (end != BreakIterator.DONE) {
        val sub = text.substring(start, end)
        if (!regexp.toRegex()
                .matches(sub) && !emojiSet.contains(sub) && !nerdFontSet.contains(sub)
        ) {
            return Result.failure(Exception("unsupported $sub"))
        }
        start = end
        end = breakIterator.next()
    }
    return UNIT_RESULT
}

actual fun safeFirstUnicode(text: String): String? {
    val breakIterator = BreakIterator.getCharacterInstance().apply {
        setText(text)
    }
    val first = breakIterator.first()
    val next = breakIterator.next()
    val sub = text.substring(first, next)
    return sub
}

actual fun safeFirstEmoji(text: String): String? {
    val breakIterator = BreakIterator.getCharacterInstance().apply {
        setText(text)
    }
    val emojiSet = UnicodeSet("[[:RGI_Emoji:]]").freeze()
    val first = breakIterator.first()
    val next = breakIterator.next()
    val sub = text.substring(first, next)
    if (emojiSet.contains(sub)) {
        return sub
    }
    return null
}

fun UnicodeSet.safeFreeze(): UnicodeSet {
    freeze()
    return this
}
