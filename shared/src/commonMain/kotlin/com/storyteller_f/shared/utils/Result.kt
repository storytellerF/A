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
    return if (isSuccess) {
        this
    } else {
        block(exceptionOrNull()!!)
    }
}

suspend fun <T1, T2> merge(result1: () -> Result<T1>, result2: () -> Result<T2>): Result<Pair<T1, T2>> {
    val r1 = result1()
    val t1 = r1.exceptionOrNull()
    if (t1 != null) return Result.failure(t1)
    val s1 = r1.getOrThrow()

    val r2 = result2()
    val t2 = r2.exceptionOrNull()
    if (t2 != null) return Result.failure(t2)
    val s2 = r2.getOrThrow()
    return Result.success(Pair(s1, s2))
}
