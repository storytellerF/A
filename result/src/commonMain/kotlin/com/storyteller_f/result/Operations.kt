/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.result

import kotlin.coroutines.cancellation.CancellationException

inline fun <T, E> captureFailure(mapError: (Throwable) -> E, block: () -> T): CustomResult<T, E> =
    try {
        CustomResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (expected: Exception) {
        CustomResult.Failure(mapError(expected))
    }

inline fun <T, E : FailureDetails, R, F> CustomResult<T, E>.andThen(
    mapError: (FailureDetails) -> F,
    block: (T) -> CustomResult<R, FailureDetails>,
): CustomResult<R, F> =
    when (this) {
        is CustomResult.Success -> block(value).mapError(mapError)
        is CustomResult.Failure -> CustomResult.Failure(mapError(error))
    }

inline fun <T, E : FailureDetails, R, F> CustomResult<T?, E>.andThenPresent(
    mapError: (FailureDetails) -> F,
    block: (T) -> CustomResult<R?, FailureDetails>,
): CustomResult<R?, F> =
    when (this) {
        is CustomResult.Success -> if (value == null) CustomResult.Success(null) else block(value).mapError(mapError)
        is CustomResult.Failure -> CustomResult.Failure(mapError(error))
    }

inline fun <T, E, R> CustomResult<T?, E>.mapPresent(block: (T) -> R?): CustomResult<R?, E> =
    map { value -> value?.let(block) }

inline fun <T, E : FailureDetails, F> CustomResult<T?, E>.requireValue(
    mapError: (FailureDetails) -> F,
    ifAbsent: () -> F,
): CustomResult<T, F> =
    when (this) {
        is CustomResult.Success -> if (value == null) CustomResult.Failure(ifAbsent()) else CustomResult.Success(value)
        is CustomResult.Failure -> CustomResult.Failure(mapError(error))
    }

inline fun <E : FailureDetails, F> CustomResult<Boolean, E>.requireTrue(
    mapError: (FailureDetails) -> F,
    ifFalse: () -> F,
): CustomResult<Unit, F> =
    when (this) {
        is CustomResult.Success -> if (value) CustomResult.Success(Unit) else CustomResult.Failure(ifFalse())
        is CustomResult.Failure -> CustomResult.Failure(mapError(error))
    }

inline fun <T, E : FailureDetails, F> CustomResult<T, E>.recover(
    mapError: (FailureDetails) -> F,
    block: (E) -> CustomResult<T, FailureDetails>,
): CustomResult<T, F> =
    when (this) {
        is CustomResult.Success -> this
        is CustomResult.Failure -> block(error).mapError(mapError)
    }

inline fun <T, E> CustomResult<T?, E>.onPresent(block: (T) -> Unit): CustomResult<T?, E> =
    onSuccess { value -> if (value != null) block(value) }

fun <T, E> CustomResult<List<T>?, E>.firstOrNull(): CustomResult<T?, E> = map { it?.firstOrNull() }

fun <T, E> CustomResult<T, E>.toUnit(): CustomResult<Unit, E> = map { }

/** A diagnostic view for APIs which require Throwable; typed consumers use errorOrNull. */
fun <T, E : FailureDetails> CustomResult<T, E>.exceptionOrNull(): Throwable? = errorOrNull()?.cause

inline fun <T, R, E> T.captureFailure(mapError: (Throwable) -> E, block: T.() -> R): CustomResult<R, E> =
    com.storyteller_f.result.captureFailure(mapError) { block(this) }

inline fun <T, E> CustomResult<T, E>.getOrElse(onFailure: (E) -> T): T =
    when (this) {
        is CustomResult.Success -> value
        is CustomResult.Failure -> onFailure(error)
    }
