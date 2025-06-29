package com.storyteller_f.shared.utils

actual fun checkContent(text: String): Boolean {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\n\\p{IsEmoji}]+$"
    return regexp.toRegex().matches(text)
}

actual fun safeFirstUnicode(text: String): String?  {
    val regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\\p{IsEmoji}]"
    return regexp.toRegex().find(text)?.groupValues?.getOrNull(0)
}

actual fun safeFirstEmoji(text: String): String? {
    val regexp = "^\\p{IsEmoji}"
    return regexp.toRegex().find(text)?.groupValues?.getOrNull(0)
}
