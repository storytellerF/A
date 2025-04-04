package com.storyteller_f

import com.impossibl.postgres.jdbc.PGSQLIntegrityConstraintViolationException
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    private var onExplainResult: ((String, String, String, String) -> Unit)? = null

    fun enableExplain(value: ((String, String, String, String) -> Unit)?) {
        onExplainResult = value
    }

    fun connect(connection: DatabaseConnection) {
        Napier.d {
            "connect $connection"
        }
        val (uri, driver, user, password) = connection
        Database.connect(uri, driver, user, password)
    }

    private val tables = arrayOf(Aids,
        Communities,
        EncryptedTopics,
        EncryptedTopicKeys,
        MediaRefs,
        MemberJoins,
        Reactions,
        Rooms,
        Titles,
        Topics,
        Users)

    fun init(dropBeforeInit: Boolean = false) {
        transaction {
            addLogger(StdOutSqlLogger)
            if (dropBeforeInit) {
                SchemaUtils.drop(*tables)
            }
            SchemaUtils.create(*tables)
        }
    }

    fun clean() {
        transaction {
            SchemaUtils.drop(*tables)
        }
    }

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): Result<T> {
        val r = Exception()
        return runCatching {
            newSuspendedTransaction(Dispatchers.IO) {
                try {
                    block()
                } catch (e: Throwable) {
                    if (e !is UnauthorizedException) {
                        Napier.e(e, "database failed") {
                            "${connection.connection}"
                        }
                    }
                    throw e
                }
            }
        }
    }

    /**
     * 带有transform
     */
    suspend fun <T, R> query(transform: T.() -> R, block: Transaction.() -> T): Result<R> =
        dbQuery { transform(block()) }

    private suspend fun <T, R> dbSearch(transform: SizedIterable<T>.() -> R, block: () -> SizedIterable<T>): Result<R> {
        val r = Exception()
        return runCatching {
            newSuspendedTransaction(Dispatchers.IO) {
                debug = onExplainResult != null
                try {
                    explainQuery(r, block)
                    transform(block())
                } catch (e: Throwable) {
                    if (e !is UnauthorizedException) {
                        r.initCause(e)
                        Napier.e(r, "database failed") {
                            "${connection.connection}"
                        }
                    }
                    throw e
                }
            }
        }
    }

    /**
     * 带有transform
     */
    suspend fun <T, R> mapQuery(transform: T.() -> R, block: () -> SizedIterable<T>): Result<List<R>> =
        dbSearch({
            map(transform)
        }) {
            block()
        }

    /**
     * 带有transform
     */
    suspend fun <T, R, R1> mapQuery(
        transform: R1.() -> R,
        resultRowTransform: (T) -> R1,
        block: () -> SizedIterable<T>
    ): Result<List<R>> =
        dbSearch({
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
        transform: R1.() -> T,
        resultRowTransform: (R) -> R1,
        block: () -> SizedIterable<R>
    ): Result<T?> = dbSearch({
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
        resultRowTransform: (R) -> R1,
        block: () -> SizedIterable<R>
    ): Result<R1?> = dbSearch({
        limit(1).firstOrNull()?.let(resultRowTransform)
    }) {
        block()
    }

    /**
     * 检查数据是不是空
     */
    suspend fun <T> isEmpty(block: () -> SizedIterable<T>): Result<Boolean> = dbSearch({
        limit(1).empty()
    }) {
        block()
    }

    suspend fun <T> isNotEmpty(block: () -> SizedIterable<T>): Result<Boolean> = dbSearch({
        !(limit(1).empty())
    }) {
        block()
    }

    suspend fun <T> count(block: () -> SizedIterable<T>): Result<Long> = dbSearch({
        count()
    }) {
        block()
    }

    private fun <T> Transaction.explainQuery(point: Exception, block: () -> SizedIterable<T>) {
        onExplainResult?.let {
            val result = explain {
                block()
            }.toList().joinToString("\n")
            assert(statements.isNotEmpty())
            val input = statements.toString().split("\n").firstOrNull {
                it.isNotEmpty() && !it.contains("INFORMATION_SCHEMA")
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
