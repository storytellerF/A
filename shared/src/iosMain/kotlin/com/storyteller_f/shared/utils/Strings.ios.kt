/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.utils

import com.storyteller_f.result.CustomResult
import com.storyteller_f.shared.model.ContentValidationError
import platform.Foundation.NSString
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex
import platform.Foundation.substringWithRange

actual fun safeFirstUnicode(text: String): String? =
    text.takeIf(String::isNotEmpty)?.let {
        val value = it as NSString
        value.substringWithRange(value.rangeOfComposedCharacterSequenceAtIndex(0uL))
    }

actual fun checkContent(text: String): CustomResult<Unit, ContentValidationError> =
    if (Regex("""^[\p{L}\p{N}\p{P}\p{Z}\p{S}\s]+$""").matches(text)) {
        CustomResult.Success(Unit)
    } else {
        CustomResult.Failure(ContentValidationError.UnsupportedCharacters("Content contains unsupported characters"))
    }

actual fun safeFirstEmoji(text: String): String? =
    safeFirstUnicode(text)?.takeIf { Regex("^\\p{Emoji_Presentation}").containsMatchIn(it) }
