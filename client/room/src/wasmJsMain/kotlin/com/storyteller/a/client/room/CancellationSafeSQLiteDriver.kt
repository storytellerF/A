/*
 * This is a private project. All rights reserved.
 */

package com.storyteller.a.client.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class CancellationSafeSQLiteDriver(private val delegate: SQLiteDriver) : SQLiteDriver by delegate {
    override suspend fun open(fileName: String): SQLiteConnection {
        val connection =
            awaitWorkerResponse {
                CancellationSafeSQLiteConnection(delegate.open(fileName))
            }
        return connection
    }
}

private class CancellationSafeSQLiteConnection(private val delegate: SQLiteConnection) : SQLiteConnection by delegate {
    override suspend fun prepare(sql: String): SQLiteStatement {
        val statement =
            awaitWorkerResponse {
                CancellationSafeSQLiteStatement(delegate.prepare(sql))
            }
        return statement
    }
}

private class CancellationSafeSQLiteStatement(private val delegate: SQLiteStatement) : SQLiteStatement by delegate {
    override suspend fun step(): Boolean = awaitWorkerResponse(delegate::step)
}

internal suspend fun <T> awaitWorkerResponse(request: suspend () -> T): T {
    currentCoroutineContext().ensureActive()
    val result = withContext(NonCancellable) { request() }
    currentCoroutineContext().ensureActive()
    return result
}
