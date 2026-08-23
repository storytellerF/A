/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv

interface LockService {
    /** Runs [block] while holding the lock identified by [key]. */
    suspend fun <T> withLock(key: String, block: suspend () -> Result<T>): Result<T>
}

interface LockServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): LockService
}
