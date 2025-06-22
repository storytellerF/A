package com.storyteller_f.a.backend.service.cache

interface CacheService<T> {
    suspend fun get(key: String, block: suspend () -> T): T
}