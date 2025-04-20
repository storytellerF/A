package com.storyteller_f.shared.utils

/**
 * L: any kind of letter from any language.
 * Z: any kind of whitespace or invisible separator.
 * P: any kind of punctuation character.
 * N: any kind of numeric character in any script.
 * S: math symbols, currency signs, dingbats, box-drawing characters, etc.
 */
expect fun checkContent(text: String): Boolean

expect fun safeFirstUnicode(text: String): String?

expect fun safeFirstEmoji(text: String): String?
