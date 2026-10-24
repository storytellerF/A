/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.core.error

import com.storyteller_f.result.ConstraintFailure
import com.storyteller_f.result.FailureDetails
import kotlin.coroutines.cancellation.CancellationException

/** Failures belonging to the Database operation boundary. */
sealed interface DatabaseError : FailureDetails {
    data class ConstraintViolation(override val cause: Throwable) :
        DatabaseError,
        ConstraintFailure
    data class Unexpected(override val cause: Throwable) : DatabaseError

    companion object {
        fun fromError(error: FailureDetails): DatabaseError = if (error is DatabaseError) error else from(error.cause)

        fun from(cause: Throwable): DatabaseError {
            if (cause is CancellationException) throw cause
            return when (cause) {
                is IllegalArgumentException -> Unexpected(cause)
                else -> Unexpected(cause)
            }
        }
    }
}
