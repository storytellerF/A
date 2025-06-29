package com.storyteller_f.a.backend.service.cache

import io.github.reactivecircus.cache4k.Cache

class Cache4KService<T : Any> : CacheService<String, T> {
    val cache = Cache.Builder<String, T>().build()
    override suspend fun get(key: String, block: suspend () -> T): T {
        return cache.get(key) { block() }
    }
}
