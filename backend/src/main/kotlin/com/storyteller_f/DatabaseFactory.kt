package com.storyteller_f

import com.storyteller_f.tables.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
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
//            addLogger(StdOutSqlLogger)

            SchemaUtils.create(
                Communities,
                CommunityJoins,
                CommunityRooms,
                EncryptedTopics,
                EncryptedTopicKeys,
                Rooms,
                RoomJoins,
                Topics,
                Users,
            )
        }
    }


    fun clean() {
        transaction {
            SchemaUtils.drop(
                Communities,
                CommunityJoins,
                CommunityRooms,
                EncryptedTopics,
                EncryptedTopicKeys,
                Rooms,
                RoomJoins,
                Topics,
                Users
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    /**
     * 带有transform
     */
    suspend fun <T, R> query(transform: (T) -> R, block: suspend () -> T): R =
        dbQuery { transform(block()) }


    /**
     * 处理可能查询不到数据的问题
     */
    suspend fun <T, R : Any?> queryNotNull(transform: T.() -> R?, block: suspend () -> T?): R? =
        query({
            it?.let { transform(it) }
        }) { block() }

    /**
     * 带有transform
     */
    suspend fun <T, R> mapQuery(transform: (T) -> R, block: suspend () -> SizedIterable<T>): List<R> =
        dbQuery { block().map(transform) }

    /**
     * 带有transform
     */
    suspend fun <T, R, R1> mapQuery(
        transform: (R1) -> R,
        typeTransform: (T) -> R1,
        block: suspend () -> SizedIterable<T>
    ): List<R> =
        dbQuery {
            block().map(typeTransform).map(transform)
        }

    /**
     * 查询第一个符合条件的数据
     *
     * @param transform 转换数据
     * @param typeTransform 主要用于将ResultRow 转换成普通数据
     */
    suspend fun <T, R, R1> first(
        transform: (R1) -> T,
        typeTransform: (R) -> R1,
        block: suspend () -> SizedIterable<R>
    ): T? = dbQuery {
        block().limit(1).firstOrNull()?.let(typeTransform)?.let { transform(it) }
    }

    /**
     * 检查数据是不是空
     */
    suspend fun <T> empty(block: suspend () -> SizedIterable<T>): Boolean = dbQuery {
        block().limit(1).empty()
    }

    suspend fun <T> count(block: suspend () -> SizedIterable<T>): Long = dbQuery {
        block().count()
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
