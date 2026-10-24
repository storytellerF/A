/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import com.storyteller_f.result.FailureDetails

/** Content validation is independent of cryptography and network failures. */
sealed interface ContentValidationError : FailureDetails {
    data class UnsupportedCharacters(val text: String) : ContentValidationError {
        override val cause: Throwable = IllegalArgumentException("Content contains unsupported characters")
    }
}
