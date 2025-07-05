package com.storyteller_f.a.backend.service.cache

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient

@Suppress("unused")
class RedisCacheService(private val endpoint: String) : CacheService<String, String> {
    override suspend fun get(key: String, block: suspend () -> String): String {
        return useRedis(endpoint) {
            val cache = get(key)
            if (cache == null) {
                val new = block()
                set(key, new)
                new
            } else {
                cache
            }
        }
    }
}

private suspend fun <R> useRedis(endpoint: String, block: suspend KredsClient.() -> R): R {
    return newClient(Endpoint.from(endpoint)).use {
        it.block()
    }
}
