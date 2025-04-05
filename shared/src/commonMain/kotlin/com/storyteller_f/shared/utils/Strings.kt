package com.storyteller_f.shared.utils

fun checkContent(content: String): Boolean {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{IsEmoji}]+$"
    return regexp.toRegex().matches(content)
}

fun String.safeFirstUnicode(): String? {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{IsEmoji}]"
    return regexp.toRegex().find(this)?.groupValues?.getOrNull(0)
}
