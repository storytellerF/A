package com.storyteller_f.backend.service

import com.storyteller_f.backend.service.tables.Aids
import com.storyteller_f.backend.service.tables.AssetTransactions
import com.storyteller_f.backend.service.tables.Communities
import com.storyteller_f.backend.service.tables.EncryptedKeys
import com.storyteller_f.backend.service.tables.MediaRefs
import com.storyteller_f.backend.service.tables.Medias
import com.storyteller_f.backend.service.tables.MemberJoins
import com.storyteller_f.backend.service.tables.ReactionRecords
import com.storyteller_f.backend.service.tables.Reactions
import com.storyteller_f.backend.service.tables.Rooms
import com.storyteller_f.backend.service.tables.TaskRecords
import com.storyteller_f.backend.service.tables.Titles
import com.storyteller_f.backend.service.tables.Topics
import com.storyteller_f.backend.service.tables.UserDevices
import com.storyteller_f.backend.service.tables.UserLogs
import com.storyteller_f.backend.service.tables.UserTopicReads
import com.storyteller_f.backend.service.tables.Users
import com.storyteller_f.shared.obj.ExplainResult
import com.storyteller_f.shared.type.UnauthorizedException
import com.storyteller_f.shared.utils.onNotNull
import com.storyteller_f.shared.utils.transformThrowable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket
import java.sql.SQLIntegrityConstraintViolationException
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
    val json = Json {
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
        runCatching {
            newSuspendedTransaction(Dispatchers.IO + MDCContext(), database) {
                explainQuery {
                    val databaseSearchConfig = DatabaseSearchConfig<R, Query>()
                    databaseSearchConfig.apply(query).searchFunc()
                }
            }
        }.onNotNull { result ->
            val r = result.copy(stackTraceString = anchor.stackTraceToString())
            suspendCancellableCoroutine { continuation ->
                thread {
                    runCatching {
                        Socket("localhost", 8888).use { socket ->
                            val os = socket.getOutputStream()
                            val writer = PrintWriter(os, true)
                            writer.println(json.encodeToString(r))
                        }
                    }.onSuccess {
                        continuation.resume(Unit)
                    }.onFailure {
                        continuation.resumeWithException(it)
                    }
                }
            }
        }.onFailure {
            throw handleDatabaseException(it, anchor)
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

object DatabaseFactory {
    fun connect(connection: DatabaseConnection): Database {
        Napier.d {
            "connect $connection"
        }
        val (uri, driver, user, password) = connection
        return Database.connect(uri, driver, user, password)
    }

    private val tables = arrayOf(
        Aids,
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

    fun init(backend: Backend) {
        Napier.i(tag = "database") {
            "init"
        }
        transaction(backend.database) {
            Napier.i(tag = "database") {
                "create tables"
            }
            SchemaUtils.create(*tables)
        }
    }

    fun clean(backend: Backend) {
        Napier.i(tag = "database") {
            "clean"
        }
        transaction(backend.database) {
            Napier.i(tag = "database") {
                "drop tables"
            }
            SchemaUtils.drop(*tables)
        }
    }
}

const val PUBLIC_KEY_LENGTH = 512
const val ADDRESS_LENGTH = 100
const val USER_NICKNAME = 20
const val COMMUNITY_NAME_LENGTH = 10
const val AID_LENGTH = 20
const val ROOM_NAME_LENGTH = 10

// 最长60，如果超过60 会进行裁切然后在后面添加uuid，保存时预留一部分空间
const val MEDIA_NAME_LENGTH = 120

fun Throwable.isDup(): Boolean {
    if (this is SQLIntegrityConstraintViolationException) return true
    if (this is ExposedSQLException) {
        val throwable = this.cause
        return (throwable is PSQLException && throwable.sqlState == PSQLState.UNIQUE_VIOLATION.state)
    }
    return false
}

private fun isConnectFailed(e: Throwable): Boolean = e is PSQLException && e.cause is ConnectException

fun Table.userPublicKey() = varchar("public_key", PUBLIC_KEY_LENGTH).uniqueIndex()
fun Table.userAddress() = varchar("pub_address", ADDRESS_LENGTH).uniqueIndex()
fun Table.userName() = varchar("nickname", USER_NICKNAME).index()

fun Table.roomName() = varchar("name", ROOM_NAME_LENGTH).index()

fun <T : Table> T.emoji() = varchar("emoji", 20)

fun Table.communityName() = varchar("name", COMMUNITY_NAME_LENGTH).index()

fun Table.titleName() = varchar("name", 20)
