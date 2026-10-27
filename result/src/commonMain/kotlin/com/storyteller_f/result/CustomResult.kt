/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.result

/** A successful value (including null), or an explicitly typed scenario error. */
sealed interface CustomResult<out T, out E> {
    data class Success<T>(val value: T) : CustomResult<T, Nothing>
    data class Failure<E>(val error: E) : CustomResult<Nothing, E>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? =
        when (this) {
            is Success -> value
            is Failure -> null
        }

    fun errorOrNull(): E? =
        when (this) {
            is Success -> null
            is Failure -> error
        }

    companion object {
        fun <T> success(value: T): CustomResult<T, Nothing> = Success(value)
        fun <E> failure(error: E): CustomResult<Nothing, E> = Failure(error)
    }
}

/** Diagnostic cause only; operation signatures still declare their own sealed error type. */
interface FailureDetails {
    val cause: Throwable
}

inline fun <T, E, R> CustomResult<T, E>.map(transform: (T) -> R): CustomResult<R, E> =
    when (this) {
        is CustomResult.Success -> CustomResult.Success(transform(value))
        is CustomResult.Failure -> this
    }

inline fun <T, E, F> CustomResult<T, E>.mapError(transform: (E) -> F): CustomResult<T, F> =
    when (this) {
        is CustomResult.Success -> this
        is CustomResult.Failure -> CustomResult.Failure(transform(error))
    }

inline fun <T, E, R> CustomResult<T, E>.fold(onSuccess: (T) -> R, onFailure: (E) -> R): R =
    when (this) {
        is CustomResult.Success -> onSuccess(value)
        is CustomResult.Failure -> onFailure(error)
    }

inline fun <T, E> CustomResult<T, E>.onSuccess(action: (T) -> Unit): CustomResult<T, E> =
    apply {
        if (this is CustomResult.Success) action(value)
    }

inline fun <T, E> CustomResult<T, E>.onFailure(action: (E) -> Unit): CustomResult<T, E> =
    apply {
        if (this is CustomResult.Failure) action(error)
    }

/** Combines operations in the same error domain; use mapError before crossing domains. */
inline fun <T, E, R> CustomResult<T, E>.flatMap(transform: (T) -> CustomResult<R, E>): CustomResult<R, E> =
    when (this) {
        is CustomResult.Success -> transform(value)
        is CustomResult.Failure -> this
    }

/** Interoperability with exception-based boundaries; prefer exhaustive branches in business code. */
fun <T, E : FailureDetails> CustomResult<T, E>.getOrThrow(): T =
    when (this) {
        is CustomResult.Success -> value
        is CustomResult.Failure -> throw error.cause
    }

/** A diagnosed constraint failure, preserved when explicitly translated between operation domains. */
interface ConstraintFailure : FailureDetails

/** Expected invalid input, independent of the exception used for diagnostics. */
interface InvalidInputFailure : FailureDetails

/** Authentication is required or no longer valid. */
interface AuthenticationFailure : FailureDetails

/** The authenticated caller lacks permission for this operation. */
interface PermissionFailure : FailureDetails
