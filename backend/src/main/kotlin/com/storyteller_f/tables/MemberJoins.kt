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

suspend fun Backend.isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?) =
    if (uid == null) {
        Result.success(false)
    } else {
        databaseSession.dbSearch {
            this.search {
                MemberJoins.selectAll().where {
                    (MemberJoins.objectId eq objectId) and (MemberJoins.uid eq uid)
                }
            }
            isNotEmpty()
        }
    }

suspend fun Backend.addRoomJoin(
    room: PrimaryKey,
    id: PrimaryKey,
    time: LocalDateTime,
) = databaseSession.dbQuery {
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

suspend fun Backend.exit(
    containerId: PrimaryKey,
    id: PrimaryKey
): Result<Int> {
    return databaseSession.dbQuery {
        MemberJoins.deleteWhere {
            with(it) {
                objectId eq containerId and (uid eq id)
            }
        }
    }
}

suspend fun Backend.addCommunityJoin(
    id: PrimaryKey,
    community: PrimaryKey,
    time: LocalDateTime,
) = databaseSession.dbQuery {
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

suspend fun Backend.getJoinedUserList(roomId: PrimaryKey) =
    databaseSession.dbSearch {
        search {
            MemberJoins.selectAll().where {
                MemberJoins.objectId eq roomId
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun Backend.getUserJoinedTime(parentIds: List<PrimaryKey>, uid: PrimaryKey) =
    databaseSession.dbSearch {
        search {
            MemberJoins.select(MemberJoins.fields).where {
                (MemberJoins.uid eq uid) and (MemberJoins.objectId inList parentIds)
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun Backend.getMemberCount(parentIds: List<PrimaryKey>) = databaseSession.dbSearch {
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
