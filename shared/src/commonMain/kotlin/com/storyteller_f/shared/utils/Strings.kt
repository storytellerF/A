package com.storyteller_f.shared.utils

/**
 * L: any kind of letter from any language.
 * Z: any kind of whitespace or invisible separator.
 * P: any kind of punctuation character.
 * N: any kind of numeric character in any script.
 * S: math symbols, currency signs, dingbats, box-drawing characters, etc.
 */
fun checkContent(content: String): Boolean {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\n\\p{IsEmoji}]+$"
    return regexp.toRegex().matches(content)
}

fun String.safeFirstUnicode(): String? {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\\p{IsEmoji}]"
    return regexp.toRegex().find(this)?.groupValues?.getOrNull(0)
}

fun String.safeFirstEmoji(): String? {
    val regexp = "^\\p{IsEmoji}"
    return regexp.toRegex().find(this)?.groupValues?.getOrNull(0)
}
