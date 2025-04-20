package com.storyteller_f.tables

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.objectType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object MemberJoins : Table() {
    val uid = customPrimaryKey("uid").index()
    val objectId = customPrimaryKey("object_id").index()
    val objectType = objectType("object_type")
    val joinTime = datetime("join_time")

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
    }
}

suspend fun isMemberJoined(backend: Backend, objectId: PrimaryKey, uid: PrimaryKey?) =
    if (uid == null) {
        Result.success(false)
    } else {
        DatabaseFactory.isNotEmpty(backend) {
            MemberJoins.selectAll().where {
                (MemberJoins.objectId eq objectId) and (MemberJoins.uid eq uid)
            }
        }
    }

suspend fun DatabaseFactory.addRoomJoin(
    backend: Backend,
    room: PrimaryKey,
    id: PrimaryKey,
    time: LocalDateTime,
    oldMemberCount: Long
) = dbQuery(backend) {
    addRoomJoinRaw(room, id, time, oldMemberCount)
}

fun addRoomJoinRaw(
    room: PrimaryKey,
    id: PrimaryKey,
    time: LocalDateTime,
    oldMemberCount: Long
) {
    check(MemberJoins.insert {
        it[joinTime] = time
        it[objectId] = room
        it[objectType] = ObjectType.ROOM
        it[uid] = id
    }.insertedCount > 0) {
        "join room failed"
    }
    check(Rooms.update({
        Rooms.id eq room and (Rooms.memberCount eq oldMemberCount)
    }) {
        it[memberCount] = oldMemberCount + 1
    } > 0) {
        "modify room member count failed"
    }
}

suspend fun DatabaseFactory.exit(
    backend: Backend,
    containerId: PrimaryKey,
    id: PrimaryKey
): Result<Int> {
    return dbQuery(backend) {
        MemberJoins.deleteWhere {
            with(it) {
                objectId eq containerId and (uid eq id)
            }
        }
    }
}

suspend fun DatabaseFactory.addCommunityJoin(
    backend: Backend,
    id: PrimaryKey,
    community: PrimaryKey,
    time: LocalDateTime,
    oldMemberCount: Long
) = dbQuery(backend) {
    addCommunityJoinRaw(id, community, time, oldMemberCount)
}

fun addCommunityJoinRaw(
    id: PrimaryKey,
    community: PrimaryKey,
    time: LocalDateTime,
    oldMemberCount: Long
) {
    check(MemberJoins.insert {
        it[joinTime] = time
        it[uid] = id
        it[objectId] = community
        it[objectType] = ObjectType.COMMUNITY
    }.insertedCount > 0) {
        "join community failed"
    }
    check(Communities.update({
        Communities.id eq community and (Communities.memberCount eq oldMemberCount)
    }) {
        it[memberCount] = oldMemberCount + 1
    } > 0) {
        "modify community member count failed"
    }
}

suspend fun DatabaseFactory.createMemberJoin(backend: Backend, join: MemberJoin) = dbQuery(
    backend
) {
    check(MemberJoins.insert { statement ->
        statement[uid] = join.uid
        statement[objectId] = join.objectId
        statement[objectType] = join.objectType
        statement[joinTime] = join.joinTime
    }.insertedCount > 0) {
        "join failed"
    }
}

suspend fun DatabaseFactory.userListJoinedRoom(backend: Backend, roomId: PrimaryKey) =
    mapQuery(backend, {
        MemberJoin.wrapRow(this)
    }) {
        MemberJoins.selectAll().where {
            MemberJoins.objectId eq roomId
        }
    }
