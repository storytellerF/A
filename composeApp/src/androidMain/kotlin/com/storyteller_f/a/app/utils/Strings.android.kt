package com.storyteller_f.a.app.utils

import com.yy.mobile.emoji.EmojiReader

actual fun String.safeFirstUnicode(): CharSequence {
    return EmojiReader.subSequence(this, 1)
}
