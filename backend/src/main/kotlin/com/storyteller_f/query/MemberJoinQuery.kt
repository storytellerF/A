package com.storyteller_f.query

import com.storyteller_f.Backend
import com.storyteller_f.isNotEmpty
import com.storyteller_f.map
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.MemberJoin
import com.storyteller_f.tables.MemberJoin.Companion.wrapRow
import com.storyteller_f.tables.MemberJoins
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

suspend fun Backend.isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?) =
    if (uid == null) {
        Result.success(false)
    } else {
        exposedDatabaseSession.dbSearch {
            search {
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
) = exposedDatabaseSession.dbQuery {
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
    return exposedDatabaseSession.dbQuery {
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
) = exposedDatabaseSession.dbQuery {
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
    exposedDatabaseSession.dbSearch {
        search {
            MemberJoins.selectAll().where {
                MemberJoins.objectId eq roomId
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun Backend.getUserJoinedTime(parentIds: List<PrimaryKey>, uid: PrimaryKey) =
    exposedDatabaseSession.dbSearch {
        search {
            MemberJoins.select(MemberJoins.fields).where {
                (MemberJoins.uid eq uid) and (MemberJoins.objectId inList parentIds)
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun Backend.getMemberCount(parentIds: List<PrimaryKey>) = exposedDatabaseSession.dbSearch {
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
