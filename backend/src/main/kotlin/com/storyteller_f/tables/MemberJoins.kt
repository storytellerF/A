package com.storyteller_f.tables

import com.storyteller_f.*
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
        index("member-joins-uid", true, uid, objectId)
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
) = dbQuery(backend) {
    addRoomJoinRaw(room, id, time)
}

fun addRoomJoinRaw(
    room: PrimaryKey,
    userId: PrimaryKey,
    time: LocalDateTime,
) {
    check(MemberJoins.insert {
        it[joinedTime] = time
        it[objectId] = room
        it[objectType] = ObjectType.ROOM
        it[this.uid] = userId
    }.insertedCount > 0) {
        "join room failed"
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
) = dbQuery(backend) {
    addCommunityJoinRaw(id, community, time)
}

fun addCommunityJoinRaw(
    id: PrimaryKey,
    community: PrimaryKey,
    time: LocalDateTime,
) {
    check(MemberJoins.insert {
        it[joinedTime] = time
        it[uid] = id
        it[objectId] = community
        it[objectType] = ObjectType.COMMUNITY
    }.insertedCount > 0) {
        "join community failed"
    }
}

suspend fun DatabaseFactory.getJoinedUserList(backend: Backend, roomId: PrimaryKey) =
    dbSearch(backend) {
        search {
            MemberJoins.selectAll().where {
                MemberJoins.objectId eq roomId
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun DatabaseFactory.getUserJoinedTime(backend: Backend, parentIds: List<PrimaryKey>, uid: PrimaryKey) =
    dbSearch(backend) {
        search {
            MemberJoins.select(MemberJoins.fields).where {
                (MemberJoins.uid eq uid) and (MemberJoins.objectId inList parentIds)
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun DatabaseFactory.getMemberCount(backend: Backend, parentIds: List<PrimaryKey>) = dbSearch(backend) {
    val column = MemberJoins.uid.countDistinct()
    search {
        MemberJoins.select(MemberJoins.objectId, column).where {
            MemberJoins.objectId inList parentIds
        }.groupBy(MemberJoins.objectId)
    }
    map {
        it[MemberJoins.objectId] to it[column]
    }
}
