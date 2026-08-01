package com.storyteller_f.shared.utils

import kotlin.coroutines.cancellation.CancellationException

val UNIT_RESULT = Result.success(Unit)

suspend fun <T, R> Result<T>.mapResult(block: suspend (T) -> Result<R>): Result<R> {
    rethrowCancellation()
    return if (isSuccess) {
        try {
            block(getOrThrow()).rethrowCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    } else {
        Result.failure(exceptionOrNull()!!)
    }
}

suspend fun <T, R> Result<T?>.mapResultIfNotNull(block: suspend (T) -> Result<R?>): Result<R?> {
    return mapResult { t ->
        if (t == null) {
            Result.success(null)
        } else {
            block(t)
        }
    }
}

suspend fun <T, R> Result<T?>.mapIfNotNull(block: suspend (T) -> R?): Result<R?> {
    rethrowCancellation()
    return map { value ->
        if (value == null) {
            null
        } else {
            block(value)
        }
    }
}

suspend fun <T, R> Result<T?>.mapCatchingNotNull(block: suspend (T) -> R?): Result<R?> {
    rethrowCancellation()
    return mapCatching { value ->
        if (value == null) {
            null
        } else {
            block(value)
        }
    }.rethrowCancellation()
}

suspend fun <T> Result<T?>.filterNotNull(block: suspend () -> Throwable): Result<T> {
    rethrowCancellation()
    return if (isSuccess) {
        val t = getOrNull()
        if (t == null) {
            Result.failure(block().rethrowCancellation())
        } else {
            Result.success(t)
        }
    } else {
        Result.failure(exceptionOrNull()!!)
    }
}

suspend fun <T> Result<T>.recoverResult(block: suspend (Throwable) -> Result<T>): Result<T> {
    rethrowCancellation()
    return if (isSuccess) {
        this
    } else {
        block(exceptionOrNull()!!).rethrowCancellation()
    }
}

suspend fun <T> Result<T>.recoverIfDup(isDup: (Throwable) -> Boolean, block: suspend () -> Result<T>): Result<T> {
    return recoverResult { e ->
        if (isDup(e)) {
            block()
        } else {
            Result.failure(e)
        }
    }
}

suspend fun <T> Result<T>.transformThrowable(block: suspend (Throwable) -> Throwable): Result<T> {
    rethrowCancellation()
    return if (isSuccess) {
        this
    } else {
        Result.failure(block(exceptionOrNull()!!).rethrowCancellation())
    }
}

suspend fun Result<Boolean>.errorIfFalse(error: () -> Exception): Result<Unit> {
    return mapResult {
        if (it) {
            UNIT_RESULT
        } else {
            Result.failure(error())
        }
    }
}

suspend fun <T> Result<T?>.ifNotNull(block: suspend (T) -> Unit) =
    rethrowCancellation().onSuccess { value ->
        if (value != null) {
            block(value)
        }
    }

/**
 * 获取列表中第一个
 */
fun <T> Result<List<T>?>.firstOrNull(): Result<T?> {
    rethrowCancellation()
    return map { it?.firstOrNull() }
}

fun <T> Result<T>.unit(): Result<Unit> {
    rethrowCancellation()
    return map {
    }
}

private fun <T> Result<T>.rethrowCancellation(): Result<T> {
    exceptionOrNull()?.rethrowCancellation()
    return this
}

private fun Throwable.rethrowCancellation(): Throwable {
    if (this is CancellationException) throw this
    return this
}
