package com.storyteller_f

import com.impossibl.postgres.jdbc.PGSQLIntegrityConstraintViolationException
import com.storyteller_f.shared.obj.UnauthorizedException
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.slf4j.MDCContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PSQLException
import java.net.ConnectException

object DatabaseFactory {

    private var onExplainResult: ((String, String, String, String) -> Unit)? = null

    fun enableExplain(value: ((String, String, String, String) -> Unit)?) {
        onExplainResult = value
    }

    fun connect(connection: DatabaseConnection): Database {
        Napier.d {
            "connect $connection"
        }
        val (uri, driver, user, password) = connection
        return Database.connect(uri, driver, user, password)
    }

    private val tables = arrayOf(
        Aids,
        Communities,
        EncryptedTopics,
        EncryptedTopicKeys,
        MediaRefs,
        Medias,
        MemberJoins,
        Reactions,
        Rooms,
        Titles,
        Topics,
        Users,
        UserLogs,
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

    private fun Transaction.handleDatabaseException(e: Throwable, point: Exception): Nothing {
        if (e is UnauthorizedException) {
            throw e
        }
        val dialectName = connection.metadata {
            databaseDialectName
        }
        val isConnectFailed = e is PSQLException && e.cause is ConnectException

        val n = if (point.cause == null) {
            point.initCause(e)
            point
        } else {
            Exception("retry failed", e)
        }
        Napier.e(n, "database failed") {
            val isRetry = point.cause != null
            if (isConnectFailed) {
                "$dialectName connect failed $isRetry"
            } else {
                "$dialectName query failed $isRetry"
            }
        }
        throw Exception(if (isConnectFailed) "database connect failed" else "database query failed", e)
    }

    suspend fun <T> dbQuery(backend: Backend, block: suspend Transaction.() -> T): Result<T> {
        val point = Exception()
        return runCatching {
            newSuspendedTransaction(Dispatchers.IO + MDCContext(), backend.database) {
                try {
                    block()
                } catch (e: Throwable) {
                    handleDatabaseException(e, point)
                }
            }
        }
    }

    /**
     * 带有transform
     */
    suspend fun <T, R> query(
        backend: Backend,
        transform: T.() -> R,
        block: Transaction.() -> T
    ): Result<R> =
        dbQuery(backend) { transform(block()) }

    private suspend fun <T, R> dbSearch(
        backend: Backend,
        transform: SizedIterable<T>.() -> R,
        block: () -> SizedIterable<T>
    ): Result<R> {
        val point = Exception()
        return runCatching {
            newSuspendedTransaction(Dispatchers.IO + MDCContext(), backend.database) {
                debug = onExplainResult != null
                try {
                    explainQuery(point, block)
                    transform(block())
                } catch (e: Throwable) {
                    handleDatabaseException(e, point)
                }
            }
        }
    }

    /**
     * 带有transform
     */
    suspend fun <T, R> mapQuery(
        backend: Backend,
        transform: (T) -> R,
        block: () -> SizedIterable<T>
    ): Result<List<R>> =
        dbSearch(backend, {
            map(transform)
        }) {
            block()
        }

    /**
     * 带有transform
     */
    suspend fun <T, R, R1> mapQuery(
        backend: Backend,
        transform: R1.() -> R,
        resultRowTransform: (T) -> R1,
        block: () -> SizedIterable<T>
    ): Result<List<R>> =
        dbSearch(backend, {
            map(resultRowTransform).map(transform)
        }) {
            block()
        }

    /**
     * 查询第一个符合条件的数据
     *
     * @param transform 转换数据
     * @param resultRowTransform 主要用于将ResultRow 转换成普通数据
     */
    suspend fun <T, R, R1> first(
        backend: Backend,
        transform: R1.() -> T,
        resultRowTransform: (R) -> R1,
        block: () -> SizedIterable<R>
    ): Result<T?> = dbSearch(backend, {
        limit(1).firstOrNull()?.let(resultRowTransform)?.let { transform(it) }
    }) {
        block()
    }

    /**
     * 查询第一个符合条件的数据
     *
     * @param resultRowTransform 主要用于将ResultRow 转换成普通数据
     */
    suspend fun <R, R1> first(
        backend: Backend,
        resultRowTransform: (R) -> R1,
        block: () -> SizedIterable<R>
    ): Result<R1?> = dbSearch(backend, {
        limit(1).firstOrNull()?.let(resultRowTransform)
    }) {
        block()
    }

    /**
     * 检查数据是不是空
     */
    suspend fun <T> isEmpty(backend: Backend, block: () -> SizedIterable<T>): Result<Boolean> =
        isNotEmpty(backend, block).map {
            !it
        }

    suspend fun <T> isNotEmpty(
        backend: Backend,
        block: () -> SizedIterable<T>
    ): Result<Boolean> = dbQuery(backend, {
        block().count()
    }).map {
        it > 0
    }

    suspend fun count(backend: Backend, block: () -> Query): Result<Long> =
        dbQuery(backend) {
            block().count()
        }

    private fun <T> Transaction.explainQuery(point: Exception, block: () -> SizedIterable<T>) {
        onExplainResult?.let {
            val result = explain {
                block()
            }.toList().joinToString("\n")
            assert(statements.isNotEmpty())
            val input = statements.toString().split("\n").firstOrNull { statement ->
                statement.isNotEmpty() && !statement.contains("INFORMATION_SCHEMA")
            }
            if (input != null) {
                val s = "explain"
                val end = input.indexOf(s, ignoreCase = true) + s.length
                val pureStatements = input.substring(end).trim()
                it(db.dialect.name, pureStatements, result, point.stackTraceToString())
            }
        }
    }
}

const val PUBLIC_KEY_LENGTH = 512
const val ADDRESS_LENGTH = 100
const val USER_NICKNAME = 20
const val COMMUNITY_NAME_LENGTH = 10
const val AID_LENGTH = 20
const val ICON_LENGTH = 1000
const val ROOM_NAME_LENGTH = 10

// 最长60，如果超过60 会进行裁切然后在后面添加uuid，保存时预留一部分空间
const val MEDIA_NAME_LENGTH = 120

fun Throwable.isDup(): Boolean {
    return this is PGSQLIntegrityConstraintViolationException
}

fun Table.userIcon() = varchar("icon", ICON_LENGTH).nullable()
fun Table.userPublicKey() = varchar("public_key", PUBLIC_KEY_LENGTH).uniqueIndex()
fun Table.userAddress() = varchar("pub_address", ADDRESS_LENGTH).uniqueIndex()
fun Table.userName() = varchar("nickname", USER_NICKNAME).index()

fun Table.roomIcon() = varchar("icon", ICON_LENGTH).nullable()
fun Table.roomName() = varchar("name", ROOM_NAME_LENGTH).index()

fun <T : Table> T.emoji() = varchar("emoji", 20)

fun Table.communityName() = varchar("name", COMMUNITY_NAME_LENGTH).index()
fun Table.communityIcon() = varchar("icon", ICON_LENGTH).nullable()

fun Table.communityPoster() = varchar("poster", ICON_LENGTH).nullable()

fun Table.titleName() = varchar("name", 20)
