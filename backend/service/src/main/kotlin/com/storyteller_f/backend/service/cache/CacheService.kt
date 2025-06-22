package com.storyteller_f.backend.service.cache

interface CacheService<T> {
    suspend fun get(key: String, block: suspend () -> T): T
}