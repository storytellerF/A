package com.storyteller_f.a.backend.simple

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.service.LockService
import com.storyteller_f.a.backend.core.service.LockServiceFactory
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NativeLockService : LockService {
    val list = mutableSetOf<String>()
    val lock = Mutex()
    override suspend fun <T> withLock(key: String, block: suspend () -> Result<T>): Result<T> {
        lock.withLock {
            if (list.contains(key)) {
                return Result.failure(IllegalStateException("key $key is locked"))
            } else {
                list.add(key)
            }
        }
        try {
            return block()
        } finally {
            // 保证清理锁操作不会被协程取消
            withContext(NonCancellable) {
                lock.withLock {
                    list.remove(key)
                }
            }
        }
    }
}

class NativeLockServiceFactory : LockServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return true
    }

    override fun build(env: MergedEnv): LockService {
        return NativeLockService()
    }
}
