package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.AdminDatabase
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
import com.storyteller_f.a.backend.exposed.database.ExposedAdminDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedCommunityDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedContainerDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedFileDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedPanelAccountDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedRoomDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedTitleDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedTopicDatabase
import com.storyteller_f.a.backend.exposed.database.ExposedUserDatabase
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import java.sql.SQLIntegrityConstraintViolationException

abstract class BaseTable : Table() {
    val id = customPrimaryKey("id")
    val createdTime = datetime("created_time")

    override val primaryKey = PrimaryKey(id)
}

fun Throwable.isDup(): Boolean {
    if (this is SQLIntegrityConstraintViolationException) return true
    if (this is R2dbcDataIntegrityViolationException) return true
    if (this is ExposedR2dbcException) {
        val throwable = cause
        if (throwable != null) {
            return throwable.isDup()
        }
    }
    return false
}

fun Table.customPrimaryKey(name: String) = long(name)

fun Table.objectType(name: String) = enumerationByName<ObjectType>(name, 20)
fun Table.memberStatus(name: String) = enumerationByName<MemberStatus>(name, 20)

fun Table.algoType(name: String) = enumerationByName<AlgoType>(name, 20).default(AlgoType.P256)
fun Table.passType(name: String) = enumerationByName<PassType>(name, 20).default(PassType.RAW)
fun Table.titleType(name: String) = enumerationByName<TitleType>(name, 10)

fun Table.titleStatus(name: String) = enumerationByName<TitleStatus>(name, 10)
fun Table.assetType(name: String) = enumerationByName<AssetType>(name, 20)
fun Table.quotaType(name: String) = enumerationByName<QuotaType>(name, 20)

fun Table.taskRecordType(name: String) = enumerationByName<TaskRecordType>(name, 20)

fun <T : Table> T.emoji() = varchar("emoji", 20)

class ExposedDatabase(val databaseSession: ExposedDatabaseSession) : CombinedDatabase {
    override val user: UserDatabase
        get() = ExposedUserDatabase(databaseSession)
    override val topic: TopicDatabase
        get() = ExposedTopicDatabase(databaseSession, container, file)
    override val title: TitleDatabase
        get() = ExposedTitleDatabase(databaseSession)
    override val community: CommunityDatabase
        get() = ExposedCommunityDatabase(databaseSession, container)
    override val room: RoomDatabase
        get() = ExposedRoomDatabase(databaseSession, container)
    override val file: FileDatabase
        get() = ExposedFileDatabase(databaseSession)
    override val container: ContainerDatabase
        get() = ExposedContainerDatabase(databaseSession)
    override val admin: AdminDatabase
        get() = ExposedAdminDatabase(databaseSession)
    override val panelAccount: PanelAccountDatabase
        get() = ExposedPanelAccountDatabase(databaseSession)

    override suspend fun init() {
        ExposedDatabaseFactory.init(databaseSession.database)
    }

    override suspend fun clean() {
        ExposedDatabaseFactory.clean(databaseSession.database)
    }

    override suspend fun migration() {
        ExposedDatabaseFactory.migration(databaseSession.database)
    }

    override fun isDup(throwable: Throwable): Boolean {
        return throwable.cause?.isDup() ?: false
    }
}

fun buildExposedDatabase(databaseConnection: DatabaseConnection): ExposedDatabase {
    val database = ExposedDatabaseFactory.connect(databaseConnection)
    val databaseSession = ExposedDatabaseSession(database, null)
    return ExposedDatabase(databaseSession)
}
