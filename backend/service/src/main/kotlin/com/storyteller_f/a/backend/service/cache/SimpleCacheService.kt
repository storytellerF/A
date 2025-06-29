package com.storyteller_f.a.backend.service.cache

import kotlin.time.Duration

class SimpleCacheService<K, V>(
    private val defaultExpireMillis: Duration,
) : CacheService<K, V> {

    private data class CacheEntry<V>(val value: V, val expiryTime: Long)

    private val cache = mutableMapOf<K, CacheEntry<V>>()

    private fun put(key: K, value: V) {
        val expireAt = System.currentTimeMillis() + defaultExpireMillis.inWholeMilliseconds
        cache[key] = CacheEntry(value, expireAt)
    }

    override suspend fun get(key: K, block: suspend () -> V): V {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() < entry.expiryTime) {
                return entry.value
            }
        }
        val value = block()
        put(key, value)
        return value
    }
}
