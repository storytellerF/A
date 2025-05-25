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

data class Merged4<M1, M2, M3, M4>(val first: M1, val second: M2, val third: M3, val fourth: M4)

inline fun <T1, T2, T3, T4> merge(
    result1: () -> Result<T1>,
    result2: () -> Result<T2>,
    result3: () -> Result<T3>,
    result4: () -> Result<T4>
): Result<Merged4<T1, T2, T3, T4>> {
    val r1 = result1()
    val t1 = r1.exceptionOrNull()
    if (t1 != null) return Result.failure(t1)
    val s1 = r1.getOrThrow()

    val r2 = result2()
    val t2 = r2.exceptionOrNull()
    if (t2 != null) return Result.failure(t2)
    val s2 = r2.getOrThrow()

    val r3 = result3()
    val t3 = r3.exceptionOrNull()
    if (t3 != null) return Result.failure(t3)
    val s3 = r3.getOrThrow()

    val r4 = result4()
    val t4 = r4.exceptionOrNull()
    if (t4 != null) return Result.failure(t4)
    val s4 = r4.getOrThrow()

    return Result.success(Merged4(s1, s2, s3, s4))
}

inline fun <T1, T2, T3> merge(
    result1: () -> Result<T1>,
    result2: () -> Result<T2>,
    result3: () -> Result<T3>
): Result<Triple<T1, T2, T3>> {
    val r1 = result1()
    val t1 = r1.exceptionOrNull()
    if (t1 != null) return Result.failure(t1)
    val s1 = r1.getOrThrow()

    val r2 = result2()
    val t2 = r2.exceptionOrNull()
    if (t2 != null) return Result.failure(t2)
    val s2 = r2.getOrThrow()

    val r3 = result3()
    val t3 = r3.exceptionOrNull()
    if (t3 != null) return Result.failure(t3)
    val s3 = r3.getOrThrow()
    return Result.success(Triple(s1, s2, s3))
}

inline fun <T1, T2> merge(result1: () -> Result<T1>, result2: () -> Result<T2>): Result<Pair<T1, T2>> {
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
