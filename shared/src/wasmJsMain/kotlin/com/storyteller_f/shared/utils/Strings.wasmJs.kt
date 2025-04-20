package com.storyteller_f.shared.utils

actual fun safeFirstUnicode(text: String): String? =
    js("text.match(/^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\\p{Emoji_Presentation}]/gu)[0]")

actual fun checkContent(text: String): Boolean =
    js("/^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}\\s\\p{Emoji_Presentation}]+$/gu.test(text)")

actual fun safeFirstEmoji(text: String): String? = js("text.match(/^\\p{Emoji_Presentation}/gu)[0]")
