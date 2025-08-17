package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.CliDatabase
import com.storyteller_f.a.backend.core.CombinedDatabase
import com.storyteller_f.a.backend.core.CommunityDatabase
import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.DatabaseConnection
import com.storyteller_f.a.backend.core.FileDatabase
import com.storyteller_f.a.backend.core.RoomDatabase
import com.storyteller_f.a.backend.core.TitleDatabase
import com.storyteller_f.a.backend.core.TopicDatabase
import com.storyteller_f.a.backend.core.UserDatabase
import com.storyteller_f.shared.type.ObjectType
import io.github.aakira.napier.Napier
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.net.ConnectException
import java.sql.SQLIntegrityConstraintViolationException

abstract class BaseTable : Table() {
    val id = customPrimaryKey("id")
    val createdTime = datetime("created_time")

    override val primaryKey = PrimaryKey(id)
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
        val throwable = cause
        Napier.e(throwable = this) {
            "dup check exception $throwable"
        }
    }
    return false
}

fun isConnectFailed(e: Throwable): Boolean = e is ConnectException

fun Table.customPrimaryKey(name: String) = long(name)

fun Table.objectType(name: String) = enumerationByName<ObjectType>(name, 10)

fun Table.userPublicKey() = varchar("public_key", PUBLIC_KEY_LENGTH).uniqueIndex()
fun Table.userPrivateKey() = varchar("private_key", PUBLIC_KEY_LENGTH).uniqueIndex()
fun Table.userAddress() = varchar("address", ADDRESS_LENGTH).uniqueIndex()
fun Table.userName() = varchar("nickname", USER_NICKNAME).index()

fun Table.roomName() = varchar("name", ROOM_NAME_LENGTH).index()

fun <T : Table> T.emoji() = varchar("emoji", 20)

fun Table.communityName() = varchar("name", COMMUNITY_NAME_LENGTH).index()

fun Table.titleName() = varchar("name", 20)

class ExposedDatabase(val databaseSession: ExposedDatabaseSession) : CombinedDatabase {
    override val userDatabase: UserDatabase
        get() = ExposedUserDatabase(databaseSession)
    override val topicDatabase: TopicDatabase
        get() = ExposedTopicDatabase(databaseSession, containerDatabase, fileDatabase)
    override val titleDatabase: TitleDatabase
        get() = ExposedTitleDatabase(databaseSession)
    override val communityDatabase: CommunityDatabase
        get() = ExposedCommunityDatabase(databaseSession, containerDatabase)
    override val roomData: RoomDatabase
        get() = ExposedRoomDatabase(databaseSession, containerDatabase)
    override val fileDatabase: FileDatabase
        get() = ExposedFileDatabase(databaseSession)
    override val containerDatabase: ContainerDatabase
        get() = ExposedContainerDatabase(databaseSession)
    override val cliDatabase: CliDatabase
        get() = ExposedCliDatabase(databaseSession)

    override suspend fun init() {
        ExposedDatabaseFactory.init(databaseSession.database)
    }

    override suspend fun clean() {
        ExposedDatabaseFactory.clean(databaseSession.database)
    }
}

fun buildExposedDatabase(databaseConnection: DatabaseConnection): ExposedDatabase {
    val database = ExposedDatabaseFactory.connect(databaseConnection)
    val databaseSession = ExposedDatabaseSession(database, null)
    return ExposedDatabase(databaseSession)
}
