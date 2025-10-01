package com.storyteller_f.a.backend.simple

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.service.CacheService
import com.storyteller_f.a.backend.core.service.CacheServiceFactory
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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

class SimpleCacheServiceFactory : CacheServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return true
    }

    override fun <K, T : Any> build(env: MergedEnv, vClass: KClass<T>): CacheService<K, T> {
        return SimpleCacheService(7.days)
    }
}
