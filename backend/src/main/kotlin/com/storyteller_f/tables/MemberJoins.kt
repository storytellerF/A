package com.storyteller_f.tables

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.objectType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.selectAll

object MemberJoins : Table() {
    val uid = customPrimaryKey("uid").index()
    val objectId = customPrimaryKey("object_id").index()
    val objectType = objectType("object_type")
    val joinTime = datetime("join_time").index()

    init {
        index("member-join-main", true, objectId, uid)
    }
}

class MemberJoin(
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val joinTime: LocalDateTime
) {
    companion object {
        fun wrapRow(row: ResultRow): MemberJoin {
            return MemberJoin(
                row[MemberJoins.uid],
                row[MemberJoins.objectId],
                row[MemberJoins.objectType],
                row[MemberJoins.joinTime]
            )
        }

        fun new(join: MemberJoin): Boolean {
            return MemberJoins.insert { statement ->
                statement[uid] = join.uid
                statement[objectId] = join.objectId
                statement[objectType] = join.objectType
                statement[joinTime] = join.joinTime
            }.insertedCount > 0
        }
    }
}

suspend fun isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?) = if (uid == null) {
    Result.success(false)
} else {
    DatabaseFactory.isNotEmpty {
        MemberJoins.selectAll().where {
            (MemberJoins.objectId eq objectId) and (MemberJoins.uid eq uid)
        }
    }
}

suspend fun addRoomJoin(
    room: PrimaryKey,
    id: PrimaryKey,
    time: LocalDateTime
) = DatabaseFactory.insert {
    MemberJoins.insert {
        it[joinTime] = time
        it[objectId] = room
        it[objectType] = ObjectType.ROOM
        it[uid] = id
    }
}

suspend fun exit(containerId: PrimaryKey, id: PrimaryKey): Result<Int> {
    return DatabaseFactory.dbQuery {
        MemberJoins.deleteWhere {
            with(it) {
                objectId eq containerId and (uid eq id)
            }
        }
    }
}

suspend fun addCommunityJoin(
    id: PrimaryKey,
    community: PrimaryKey,
    time: LocalDateTime
) = DatabaseFactory.insert {
    MemberJoins.insert {
        it[joinTime] = time
        it[uid] = id
        it[objectId] = community
        it[objectType] = ObjectType.COMMUNITY
    }
}
