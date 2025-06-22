package com.storyteller_f.backend.service.lock

interface LockService {
    suspend fun<T> withLock(key: String, block: suspend () -> Result<T>) : Result<T>
}