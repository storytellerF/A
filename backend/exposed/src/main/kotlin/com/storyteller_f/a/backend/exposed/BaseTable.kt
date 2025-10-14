package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.CliDatabase
import com.storyteller_f.a.backend.core.CombinedDatabase
import com.storyteller_f.a.backend.core.CommunityDatabase
import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.DatabaseConnection
import com.storyteller_f.a.backend.core.FileDatabase
import com.storyteller_f.a.backend.core.PanelAccountDatabase
import com.storyteller_f.a.backend.core.RoomDatabase
import com.storyteller_f.a.backend.core.TitleDatabase
import com.storyteller_f.a.backend.core.TopicDatabase
import com.storyteller_f.a.backend.core.UserDatabase
import com.storyteller_f.shared.type.ObjectType
import io.github.aakira.napier.Napier
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.sql.SQLIntegrityConstraintViolationException

abstract class BaseTable : Table() {
    val id = customPrimaryKey("id")
    val createdTime = datetime("created_time")

    override val primaryKey = PrimaryKey(id)
}

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

fun Table.customPrimaryKey(name: String) = long(name)

fun Table.objectType(name: String) = enumerationByName<ObjectType>(name, 10)

fun <T : Table> T.emoji() = varchar("emoji", 20)

class ExposedDatabase(val databaseSession: ExposedDatabaseSession) : CombinedDatabase {
    override val userDatabase: UserDatabase
        get() = ExposedUserDatabase(databaseSession)
    override val topicDatabase: TopicDatabase
        get() = ExposedTopicDatabase(databaseSession, containerDatabase, fileDatabase)
    override val titleDatabase: TitleDatabase
        get() = ExposedTitleDatabase(databaseSession)
    override val communityDatabase: CommunityDatabase
        get() = ExposedCommunityDatabase(databaseSession, containerDatabase)
    override val roomDatabase: RoomDatabase
        get() = ExposedRoomDatabase(databaseSession, containerDatabase)
    override val fileDatabase: FileDatabase
        get() = ExposedFileDatabase(databaseSession)
    override val containerDatabase: ContainerDatabase
        get() = ExposedContainerDatabase(databaseSession)
    override val cliDatabase: CliDatabase
        get() = ExposedCliDatabase(databaseSession)
    override val panelAccountDatabase: PanelAccountDatabase
        get() = ExposedPanelAccountDatabase(databaseSession)

    override suspend fun init() {
        ExposedDatabaseFactory.init(databaseSession.database)
    }

    override suspend fun clean() {
        ExposedDatabaseFactory.clean(databaseSession.database)
    }

    override fun isDup(throwable: Throwable): Boolean {
        return throwable.isDup()
    }
}

fun buildExposedDatabase(databaseConnection: DatabaseConnection): ExposedDatabase {
    val database = ExposedDatabaseFactory.connect(databaseConnection)
    val databaseSession = ExposedDatabaseSession(database, null)
    return ExposedDatabase(databaseSession)
}
