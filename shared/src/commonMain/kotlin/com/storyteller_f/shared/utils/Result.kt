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

suspend fun <T> Result<T?>.filterNotNull(block: suspend () -> Throwable): Result<T> {
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

suspend fun <T> Result<T>.recoverResult(block: suspend (Throwable) -> Result<T>): Result<T> {
    return if (isSuccess) {
        this
    } else {
        block(exceptionOrNull()!!)
    }
}

suspend fun <T> Result<T>.transformThrowable(block: suspend (Throwable) -> Throwable): Result<T> {
    return if (isSuccess) {
        this
    } else {
        Result.failure(block(exceptionOrNull()!!))
    }
}

data class Tuple4<M1, M2, M3, M4>(val first: M1, val second: M2, val third: M3, val fourth: M4)
data class Tuple5<M1, M2, M3, M4, M5>(val first: M1, val second: M2, val third: M3, val fourth: M4, val five: M5)

inline fun <T1, T2, T3, T4, T5> merge(
    result1: () -> Result<T1>,
    result2: () -> Result<T2>,
    result3: () -> Result<T3>,
    result4: () -> Result<T4>,
    result5: () -> Result<T5>,
) = runCatching {
    val s1 = result1().getOrThrow()
    val s2 = result2().getOrThrow()
    val s3 = result3().getOrThrow()
    val s4 = result4().getOrThrow()
    val s5 = result5().getOrThrow()
    Tuple5(s1, s2, s3, s4, s5)
}

inline fun <T1, T2, T3, T4> merge(
    result1: () -> Result<T1>,
    result2: () -> Result<T2>,
    result3: () -> Result<T3>,
    result4: () -> Result<T4>
) = runCatching {
    val s1 = result1().getOrThrow()
    val s2 = result2().getOrThrow()
    val s3 = result3().getOrThrow()
    val s4 = result4().getOrThrow()
    Tuple4(s1, s2, s3, s4)
}

inline fun <T1, T2, T3> merge(
    result1: () -> Result<T1>,
    result2: () -> Result<T2>,
    result3: () -> Result<T3>
) = runCatching {
    val r1 = result1().getOrThrow()
    val r2 = result2().getOrThrow()
    val r3 = result3().getOrThrow()
    Triple(r1, r2, r3)
}

inline fun <T1, T2> merge(result1: () -> Result<T1>, result2: () -> Result<T2>) =
    runCatching {
        val r1 = result1().getOrThrow()
        val r2 = result2().getOrThrow()
        r1 to r2
    }
