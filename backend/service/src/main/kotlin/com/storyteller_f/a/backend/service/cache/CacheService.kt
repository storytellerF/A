package com.storyteller_f.a.backend.service.cache

interface CacheService<K, T> {
    suspend fun get(key: K, block: suspend () -> T): T
}
