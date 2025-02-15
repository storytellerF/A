package com.storyteller_f

import com.impossibl.postgres.jdbc.PGSQLIntegrityConstraintViolationException
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    private var isEnableExplain = false

    fun enableExplain() {
        isEnableExplain = true
    }

    private fun connect(connection: DatabaseConnection) {
        val (uri, driver, user, password) = connection
        Database.connect(uri, driver, user, password)
    }

    fun clean(connection: DatabaseConnection) {
        connect(connection)
        clean()
    }

    fun init(connection: DatabaseConnection) {
        connect(connection)
        init()
    }

    private fun init() {
        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.create(
                Communities,
                MemberJoins,
                EncryptedTopics,
                EncryptedTopicKeys,
                Rooms,
                Topics,
                Users,
                Reactions,
                Aids,
                Titles
            )
        }
    }

    fun clean() {
        transaction {
            SchemaUtils.drop(
                Communities,
                MemberJoins,
                EncryptedTopics,
                EncryptedTopicKeys,
                Rooms,
                Topics,
                Users,
                Reactions,
                Aids,
                Titles
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): Result<T> {
        val r = Exception()
        return runCatching {
            newSuspendedTransaction(Dispatchers.IO) {
                debug = true
                try {
                    block()
                } catch (e: Throwable) {
                    if (e !is UnauthorizedException) {
                        Napier.e(e, "database failed") {
                            "$statements\nat ${r.stackTraceToString()}"
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

    /**
     * 带有transform
     */
    suspend fun <T, R> mapQuery(transform: T.() -> R, block: () -> SizedIterable<T>): Result<List<R>> =
        dbQuery {
            explainQuery(block)
            block().map(transform)
        }

    /**
     * 带有transform
     */
    suspend fun <T, R, R1> mapQuery(
        transform: R1.() -> R,
        resultRowTransform: (T) -> R1,
        block: () -> SizedIterable<T>
    ): Result<List<R>> =
        dbQuery {
            explainQuery(block)
            block().map(resultRowTransform).map(transform)
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
    ): Result<T?> = dbQuery {
        explainQuery(block)
        block().limit(1).firstOrNull()?.let(resultRowTransform)?.let { transform(it) }
    }

    /**
     * 查询第一个符合条件的数据
     *
     * @param resultRowTransform 主要用于将ResultRow 转换成普通数据
     */
    suspend fun <R, R1> first(
        resultRowTransform: (R) -> R1,
        block: () -> SizedIterable<R>
    ): Result<R1?> = dbQuery {
        explainQuery(block)
        block().limit(1).firstOrNull()?.let(resultRowTransform)
    }

    /**
     * 检查数据是不是空
     */
    suspend fun <T> isEmpty(block: () -> SizedIterable<T>): Result<Boolean> = dbQuery {
        explainQuery(block)
        block().limit(1).empty()
    }

    suspend fun <T> isNotEmpty(block: () -> SizedIterable<T>): Result<Boolean> = dbQuery {
        explainQuery(block)
        !block().limit(1).empty()
    }

    suspend fun <T> count(block: () -> SizedIterable<T>): Result<Long> = dbQuery {
        explainQuery(block)
        block().count()
    }

    suspend fun insert(block: () -> InsertStatement<Number>): Result<Int> = dbQuery {
        block().insertedCount
    }

    private fun <T> Transaction.explainQuery(block: () -> SizedIterable<T>) {
        if (isEnableExplain) {
            val result = explain {
                block()
            }.toList().joinToString("\n")
            if (result.contains("Sort") ||
                result.contains("GroupAggregate") ||
                result.contains("Seq") ||
                result.contains(
                    "Nested Loop"
                )
            ) {
                Napier.i(tag = "explain result") {
                    "result: $result\nstatements: $statements"
                }
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
