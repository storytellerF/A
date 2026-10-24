/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.utils

import com.storyteller_f.result.CustomResult
import com.storyteller_f.shared.model.ContentValidationError

actual fun safeFirstUnicode(text: String): String? =
    js("""text.match(/^[\p{L}\p{N}\p{P}\p{Z}\p{S}\p{Emoji_Presentation}]/gu)[0]""")

actual fun checkContent(text: String): CustomResult<Unit, ContentValidationError> =
    if (checkContentRaw(text)) {
    CustomResult.Success(Unit)
} else {
    CustomResult.Failure(ContentValidationError.UnsupportedCharacters("invalid content"))
}

@Suppress("UnusedParameter")
private fun checkContentRaw(text: String): Boolean =
    js("""/^[\p{L}\p{N}\p{P}\p{Z}\p{S}\s\p{Emoji_Presentation}]+$/gu.test(text)""")

actual fun safeFirstEmoji(text: String): String? = js("text.match(/^\\p{Emoji_Presentation}/gu)[0]")
