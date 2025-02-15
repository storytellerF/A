package com.storyteller_f.shared.utils

suspend fun <T, R> Result<T>.mapResult(block: suspend (T) -> Result<R>): Result<R> {
    return if (isSuccess) {
        try {
            block(getOrThrow())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    } else {
        Result.failure(exceptionOrNull()!!)
    }
}

suspend fun <T, R> Result<T?>.mapResultNotNull(block: suspend (T) -> Result<R?>): Result<R?> {
    return mapResult { t ->
        if (t == null) {
            Result.success(null)
        } else {
            block(t)
        }
    }
}

suspend fun <T, R> Result<T?>.mapNotNull(block: suspend (T) -> R?): Result<R?> {
    return map { value ->
        if (value == null) {
            null
        } else {
            block(value)
        }
    }
}

suspend fun <T, R> Result<T?>.mapCatchingNotNull(block: suspend (T) -> R?): Result<R?> {
    return mapCatching { value ->
        if (value == null) {
            null
        } else {
            block(value)
        }
    }
}

suspend fun <T> Result<T?>.filterNull(block: suspend () -> Throwable): Result<T> {
    return if (isSuccess) {
        val t = getOrNull()
        if (t == null) {
            Result.failure(block())
        } else {
            Result.success(t)
        }
    } else {
        Result.failure(exceptionOrNull()!!)
    }
}

suspend fun <T> Result<T>.recoverError(block: suspend (Throwable) -> Result<T>): Result<T> {
    if (isSuccess) {
        return this
    } else {
        return block(exceptionOrNull()!!)
    }
}
