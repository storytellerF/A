/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import com.storyteller_f.result.CustomResult
import com.storyteller_f.result.FailureDetails
import com.storyteller_f.result.InvalidInputFailure
import kotlin.coroutines.cancellation.CancellationException

sealed interface CryptoError : FailureDetails {
    data class InvalidInput(override val cause: Throwable) :
        CryptoError,
        InvalidInputFailure
    data class OperationFailed(override val cause: Throwable) : CryptoError

    companion object {
        fun from(cause: Throwable): CryptoError {
            if (cause is CancellationException) throw cause
            return when (cause) {
                is IllegalArgumentException -> InvalidInput(cause)
                else -> OperationFailed(cause)
            }
        }
    }
}

/** Captures provider failures without converting coroutine cancellation into a crypto error. */
inline fun <T> captureCrypto(block: () -> T): CustomResult<T, CryptoError> =
    try {
    CustomResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (expected: Exception) {
    CustomResult.Failure(CryptoError.from(expected))
}
