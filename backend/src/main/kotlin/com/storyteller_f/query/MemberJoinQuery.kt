package com.storyteller_f.query

import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.isNotEmpty
import com.storyteller_f.map
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.MemberJoin
import com.storyteller_f.tables.MemberJoins
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

suspend fun ExposedDatabaseSession.isMemberJoined(
    objectId: PrimaryKey,
    uid: PrimaryKey?
) =
    if (uid == null) {
        Result.success(false)
    } else {
        dbSearch {
            search {
                MemberJoins.selectAll().where {
                    (MemberJoins.objectId eq objectId) and (MemberJoins.uid eq uid)
                }
            }
            isNotEmpty()
        }
    }

suspend fun ExposedDatabaseSession.addRoomJoin(
    room: PrimaryKey,
    id: PrimaryKey,
    time: LocalDateTime,
) = dbQuery {
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

suspend fun ExposedDatabaseSession.exit(
    containerId: PrimaryKey,
    id: PrimaryKey
): Result<Int> {
    return dbQuery {
        MemberJoins.deleteWhere {
            with(it) {
                objectId eq containerId and (uid eq id)
            }
        }
    }
}

suspend fun ExposedDatabaseSession.addCommunityJoin(
    id: PrimaryKey,
    community: PrimaryKey,
    time: LocalDateTime,
) = dbQuery {
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

suspend fun ExposedDatabaseSession.getJoinedUserList(roomId: PrimaryKey) =
    dbSearch {
        search {
            MemberJoins.selectAll().where {
                MemberJoins.objectId eq roomId
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun ExposedDatabaseSession.getUserJoinedTime(
    parentIds: List<PrimaryKey>,
    uid: PrimaryKey
) =
    dbSearch {
        search {
            MemberJoins.select(MemberJoins.fields).where {
                (MemberJoins.uid eq uid) and (MemberJoins.objectId inList parentIds)
            }
        }
        map(MemberJoin::wrapRow)
    }

suspend fun ExposedDatabaseSession.getMemberCount(parentIds: List<PrimaryKey>) = dbSearch {
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
