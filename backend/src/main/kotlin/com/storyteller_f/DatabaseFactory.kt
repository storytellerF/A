package com.storyteller_f

import com.impossibl.postgres.jdbc.PGSQLIntegrityConstraintViolationException
import com.storyteller_f.tables.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

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
                Users
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): Result<T> =
        runCatching {
            newSuspendedTransaction(Dispatchers.IO) { block() }
        }

    /**
     * 带有transform
     */
    suspend fun <T, R> query(transform: T.() -> R, block: suspend () -> T): Result<R> =
        dbQuery { transform(block()) }

    /**
     * 处理可能查询不到数据的问题
     */
    suspend fun <T, R : Any?> queryNotNull(transform: T.() -> R?, block: suspend () -> T?): Result<R?> =
        query({
            this?.let { transform(it) }
        }) { block() }

    /**
     * 处理可能查询不到数据的问题
     */
    suspend fun <T, R : Any?, R1> queryNotNull(
        transform: R1.() -> R?,
        resultRowTransform: (T) -> R1,
        block: suspend () -> T?
    ): Result<R?> = queryNotNull({
        resultRowTransform(this).transform()
    }) { block() }

    /**
     * 带有transform
     */
    suspend fun <T, R> mapQuery(transform: T.() -> R, block: suspend () -> SizedIterable<T>): Result<List<R>> =
        dbQuery { block().map(transform) }

    /**
     * 带有transform
     */
    suspend fun <T, R, R1> mapQuery(
        transform: R1.() -> R,
        resultRowTransform: (T) -> R1,
        block: suspend () -> SizedIterable<T>
    ): Result<List<R>> =
        dbQuery {
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
        block: suspend () -> SizedIterable<R>
    ): Result<T?> = dbQuery {
        block().limit(1).firstOrNull()?.let(resultRowTransform)?.let { transform(it) }
    }

    /**
     * 检查数据是不是空
     */
    suspend fun <T> isEmpty(block: suspend () -> SizedIterable<T>): Result<Boolean> = dbQuery {
        block().limit(1).empty()
    }

    suspend fun <T> isNotEmpty(block: suspend () -> SizedIterable<T>): Result<Boolean> = dbQuery {
        !block().limit(1).empty()
    }

    suspend fun <T> count(block: suspend () -> SizedIterable<T>): Result<Long> = dbQuery {
        block().count()
    }

    suspend fun insert(block: suspend () -> InsertStatement<Number>): Result<Int> = dbQuery {
        block().insertedCount
    }
}

const val PUBLIC_KEY_LENGTH = 512
const val ADDRESS_LENGTH = 100
const val USER_ID_LENGTH = 20
const val USER_NICKNAME = 20
const val COMMUNITY_ID_LENGTH = 20
const val COMMUNITY_NAME_LENGTH = 10
const val ROOM_ID_LENGTH = 20
const val ICON_LENGTH = 1000
const val ROOM_NAME_LENGTH = 10

fun Throwable.isDup(): Boolean {
    return this is PGSQLIntegrityConstraintViolationException
}