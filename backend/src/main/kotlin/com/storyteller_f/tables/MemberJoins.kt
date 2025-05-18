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
    val joinedTime = datetime("joined_time")

    init {
        index("member-joins-main", true, objectId, uid)
    }
}

class MemberJoin(
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val joinedTime: LocalDateTime
) {
    companion object {
        fun wrapRow(row: ResultRow): MemberJoin {
            return with(MemberJoins) {
                MemberJoin(
                    row[uid],
                    row[objectId],
                    row[objectType],
                    row[joinedTime]
                )
            }
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
    userId: PrimaryKey,
    time: LocalDateTime,
    oldMemberCount: Long
) {
    check(MemberJoins.insert {
        it[joinedTime] = time
        it[objectId] = room
        it[objectType] = ObjectType.ROOM
        it[this.uid] = userId
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
        it[joinedTime] = time
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

suspend fun DatabaseFactory.getJoinedUserList(backend: Backend, roomId: PrimaryKey) =
    mapQuery(backend, MemberJoin::wrapRow) {
        MemberJoins.selectAll().where {
            MemberJoins.objectId eq roomId
        }
    }
