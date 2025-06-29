package com.storyteller_f.a.exposed

import com.storyteller_f.a.backend.core.DatabaseConnection
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.exposed.tables.Aids
import com.storyteller_f.a.exposed.tables.AlternateAccounts
import com.storyteller_f.a.exposed.tables.AssetTransactions
import com.storyteller_f.a.exposed.tables.Communities
import com.storyteller_f.a.exposed.tables.EncryptedKeys
import com.storyteller_f.a.exposed.tables.MediaRefs
import com.storyteller_f.a.exposed.tables.Medias
import com.storyteller_f.a.exposed.tables.MemberJoins
import com.storyteller_f.a.exposed.tables.ReactionRecords
import com.storyteller_f.a.exposed.tables.Reactions
import com.storyteller_f.a.exposed.tables.Rooms
import com.storyteller_f.a.exposed.tables.TaskRecords
import com.storyteller_f.a.exposed.tables.Titles
import com.storyteller_f.a.exposed.tables.Topics
import com.storyteller_f.a.exposed.tables.UserDevices
import com.storyteller_f.a.exposed.tables.UserLogs
import com.storyteller_f.a.exposed.tables.UserTopicReads
import com.storyteller_f.a.exposed.tables.Users
import com.storyteller_f.shared.obj.ExplainResult
import com.storyteller_f.shared.utils.transformThrowable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DatabaseSearchConfig<R, Q> {
    lateinit var searchFunc: () -> Q
    lateinit var transformFunc: Q.() -> R

    fun search(block: () -> Q) {
        searchFunc = block
    }

    fun transform(block: Q.() -> R) {
        transformFunc = block
    }
}

fun <R> DatabaseSearchConfig<R?, Query>.first(block: (ResultRow) -> R) {
    transformFunc = {
        firstOrNull()?.let(block)
    }
}

fun <T> DatabaseSearchConfig<List<T>, Query>.map(block: (ResultRow) -> T) {
    transformFunc = {
        map {
            block(it)
        }
    }
}

fun DatabaseSearchConfig<Long, Query>.count() {
    transformFunc = {
        count()
    }
}

fun DatabaseSearchConfig<Boolean, Query>.isEmpty() {
    transformFunc = {
        count() == 0L
    }
}

fun DatabaseSearchConfig<Boolean, Query>.isNotEmpty() {
    transformFunc = {
        count() != 0L
    }
}

class ExposedDatabaseSession(val database: Database, val buildType: String) {
    companion object {
        val json by lazy {
            Json {
            }
        }
    }

    private fun handleDatabaseException(e: Throwable, anchor: Throwable): Throwable {
        if (e is UnauthorizedException || e.isDup()) {
            return e
        }
        val dialectName = database.dialect.name
        val isConnectFailed = isConnectFailed(e)
        anchor.initCause(e)
        return Exception(
            if (isConnectFailed) {
                "$dialectName connect failed"
            } else {
                "$dialectName query failed"
            },
            anchor
        )
    }

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): Result<T> {
        val anchor = Exception("dbQuery")
        return runCatching {
            newSuspendedTransaction(Dispatchers.IO + MDCContext(), database) {
                maxAttempts = 1
                block()
            }
        }.transformThrowable {
            handleDatabaseException(it, anchor)
        }
    }

    suspend fun <R> dbSearch(
        query: DatabaseSearchConfig<R, Query>.() -> Unit,
    ): Result<R> {
        val anchor = Exception()
        if (buildType == "test") {
            explainQuery(query)
        }
        return runCatching {
            newSuspendedTransaction(Dispatchers.IO + MDCContext(), database) {
                maxAttempts = 1
                val databaseSearchConfig = DatabaseSearchConfig<R, Query>()
                databaseSearchConfig.apply(query).let {
                    it.transformFunc(it.searchFunc())
                }
            }
        }.transformThrowable {
            handleDatabaseException(it, anchor)
        }
    }

    private suspend fun <R> explainQuery(query: DatabaseSearchConfig<R, Query>.() -> Unit) {
        val anchor = Exception()
        try {
            val explainResult = newSuspendedTransaction(Dispatchers.IO + MDCContext(), database) {
                explainQuery {
                    val databaseSearchConfig = DatabaseSearchConfig<R, Query>()
                    databaseSearchConfig.apply(query).searchFunc()
                }
            }
            if (explainResult != null) {
                val result = explainResult.copy(stackTraceString = anchor.stackTraceToString())
                suspendCancellableCoroutine { continuation ->
                    thread {
                        sendExplainResult(result, continuation)
                    }
                }
            }
        } catch (e: Exception) {
            throw handleDatabaseException(e, anchor)
        }
    }

    private fun sendExplainResult(
        result: ExplainResult,
        continuation: CancellableContinuation<Unit>
    ) {
        runCatching {
            Socket("localhost", 8888).use { socket ->
                socket.getOutputStream().use {
                    PrintWriter(it, true).use { writer ->
                        writer.println(json.encodeToString(result))
                    }
                }
            }
        }.onSuccess {
            continuation.resume(Unit)
        }.onFailure {
            continuation.resumeWithException(it)
        }
    }

    private fun <T> Transaction.explainQuery(block: () -> SizedIterable<T>): ExplainResult? {
        debug = true
        val result = explain {
            block()
        }.toList().joinToString("\n")
        assert(statements.isNotEmpty())
        val input = statements.toString().split("\n").firstOrNull { statement ->
            statement.isNotEmpty() && !statement.contains("INFORMATION_SCHEMA")
        }
        return if (input != null) {
            val s = "explain"
            val end = input.indexOf(s, ignoreCase = true) + s.length
            val trimmedStatements = input.substring(end).trim()
            ExplainResult(db.dialect.name, trimmedStatements, result, "")
        } else {
            null
        }
    }
}

object ExposedDatabaseFactory {
    private val tables = arrayOf(
        Aids,
        AlternateAccounts,
        AssetTransactions,
        Communities,
        EncryptedKeys,
        MediaRefs,
        Medias,
        MemberJoins,
        Reactions,
        ReactionRecords,
        Rooms,
        Titles,
        Topics,
        Users,
        UserLogs,
        UserDevices,
        UserTopicReads,
        TaskRecords
    )

    fun connect(connection: DatabaseConnection): Database {
        Napier.d {
            "connect $connection"
        }
        val (uri, driver, user, password) = connection
        return Database.Companion.connect(uri, driver, user, password)
    }

    fun init(database: Database) {
        Napier.i(tag = "database") {
            "init"
        }
        transaction(database) {
            Napier.i(tag = "database") {
                "create tables"
            }
            SchemaUtils.create(*tables)
        }
    }

    fun clean(database: Database) {
        Napier.i(tag = "database") {
            "clean"
        }
        transaction(database) {
            Napier.i(tag = "database") {
                "drop tables"
            }
            SchemaUtils.drop(*tables)
        }
    }
}
