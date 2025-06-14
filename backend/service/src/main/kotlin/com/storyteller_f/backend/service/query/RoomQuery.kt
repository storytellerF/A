package com.storyteller_f.backend.service.query

import com.storyteller_f.*
import com.storyteller_f.backend.service.AID_LENGTH
import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.ObjectFetch
import com.storyteller_f.backend.service.ObjectListFetch
import com.storyteller_f.backend.service.bindPaginationQuery
import com.storyteller_f.backend.service.count
import com.storyteller_f.backend.service.first
import com.storyteller_f.backend.service.map
import com.storyteller_f.backend.service.tables.Aids
import com.storyteller_f.backend.service.tables.MemberJoins
import com.storyteller_f.backend.service.tables.Room
import com.storyteller_f.backend.service.tables.RoomRawResult
import com.storyteller_f.backend.service.tables.Rooms
import com.storyteller_f.backend.service.tables.UserTopicReads
import com.storyteller_f.backend.service.tables.Users
import com.storyteller_f.backend.service.types.PaginationResult
import com.storyteller_f.backend.service.types.PrimaryKeyFetch
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import org.jetbrains.exposed.sql.*
import kotlin.collections.plus

suspend fun ExposedDatabaseSession.checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?> {
    return dbSearch {
        search {
            Room.Companion.findRoomById(roomId)
        }
        first {
            it[Rooms.communityId] == null
        }
    }
}

suspend fun ExposedDatabaseSession.getRoomPaginationResult(
    uid: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<RoomRawResult>> {
    val joinSearch = joinStatusSearch.toJoinSearch(uid)
    return dbSearch {
        search {
            Rooms
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value)
                .buildRoomSearchWhereQuery(joinSearch, community, word)
                .bindPaginationQuery(Rooms, primaryKeyFetch)
        }
        map(Room.Companion::wrapRow)
    }.mapResult {
        processRoomListToRoomRawResult(uid, it).mapResult { list ->
            dbSearch {
                search {
                    Rooms.select(Rooms.id).buildRoomSearchWhereQuery(joinSearch, community, word)
                }
                count()
            }.map { count ->
                PaginationResult(list, count)
            }
        }
    }
}

private fun Query.buildRoomSearchWhereQuery(
    joinStatusSearch: JoinSearch,
    community: PrimaryKey?,
    word: String?,
): Query {
    if (community != null) {
        andWhere {
            Rooms.communityId eq community
        }
    }
    if (!word.isNullOrBlank()) {
        andWhere {
            Rooms.name like "%$word%"
        }
    }
    when (joinStatusSearch) {
        is JoinSearch.Joined -> adjustColumnSet {
            this.join(MemberJoins, JoinType.INNER, Rooms.id, MemberJoins.objectId) {
                MemberJoins.uid eq joinStatusSearch.uid
            }
        }

        is JoinSearch.NotJoined -> where {
            Rooms.id notInSubQuery (MemberJoins.select(MemberJoins.objectId).where {
                MemberJoins.uid eq joinStatusSearch.uid
            }) and Rooms.communityId.isNotNull()
        }

        is JoinSearch.Unspecified -> {
            val uid = joinStatusSearch.uid
            if (uid != null) {
                adjustColumnSet {
                    this.join(MemberJoins, JoinType.LEFT, Rooms.id, MemberJoins.objectId) {
                        (MemberJoins.uid eq uid)
                    }
                }.andWhere {
                    (MemberJoins.uid.isNull() and Rooms.communityId.isNotNull()).or(MemberJoins.uid.isNotNull())
                }
            } else {
                andWhere {
                    Rooms.communityId.isNotNull()
                }
            }
        }
    }
    return this
}

suspend fun ExposedDatabaseSession.getRoomCommunityId(parentId: PrimaryKey): Result<PrimaryKey?> =
    dbSearch {
        search {
            Room.Companion.findRoomById(parentId)
        }
        first {
            it[Rooms.communityId]
        }
    }

suspend fun ExposedDatabaseSession.getRoomPubKeyPaginationResult(
    roomId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<UserPubKeyInfo>> {
    return dbSearch {
        search {
            buildRoomPubKeyQuery(roomId, false).bindPaginationQuery(Users, primaryKeyFetch)
        }
        map {
            UserPubKeyInfo(it[Users.id], it[Users.publicKey])
        }
    }.mapResult { data ->
        dbSearch {
            search {
                buildRoomPubKeyQuery(roomId, true)
            }
            count()
        }.map { value ->
            PaginationResult(data, value)
        }
    }
}

fun buildRoomPubKeyQuery(roomId: PrimaryKey, getCount: Boolean): Query {
    val join = Users.join(MemberJoins, JoinType.INNER, Users.id, MemberJoins.uid)
    return if (getCount) {
        join
            .selectAll()
            .where {
                MemberJoins.objectId eq roomId
            }
    } else {
        join
            .select(Users.id, Users.publicKey)
            .where {
                MemberJoins.objectId eq roomId
            }
    }
}

suspend fun ExposedDatabaseSession.getRoomRawResult(
    objectFetch: ObjectFetch,
    fillJoinInfo: Boolean? = null,
    uid: PrimaryKey? = null,
): Result<RoomRawResult?> {
    if (uid == null && fillJoinInfo == true) return Result.failure(UnauthorizedException())
    return dbSearch {
        search {
            Rooms
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value)
                .where {
                    when (objectFetch) {
                        is ObjectFetch.AidFetch -> Aids.value eq objectFetch.aid
                        is ObjectFetch.IdFetch -> Rooms.id eq objectFetch.id
                    }
                }
        }
        first(Room.Companion::wrapRow)
    }.mapResultIfNotNull { room ->
        processRoomListToRoomRawResult(uid, listOf(room)).map {
            it.first()
        }
    }
}

private suspend fun ExposedDatabaseSession.processRoomListToRoomRawResult(
    uid: PrimaryKey?,
    rooms: List<Room>
): Result<List<RoomRawResult>> = getContainerInfo(rooms.map {
    it.id
}, uid).map { (joinedTimeMap, lastReadMap, memberCountMap) ->
    rooms.map { room ->
        RoomRawResult(
            room,
            room.icon,
            joinedTimeMap[room.id]?.joinedTime,
            lastReadMap[room.id]?.topicId,
            memberCountMap[room.id] ?: 0,
        )
    }
}

suspend fun ExposedDatabaseSession.createRoom(room: Room) = dbQuery {
    check(Rooms.insert { statement ->
        statement[Rooms.id] = room.id
        statement[Rooms.createdTime] = room.createdTime
        statement[Rooms.name] = room.name
        statement[Rooms.icon] = room.icon
        statement[Rooms.creator] = room.creator
        statement[Rooms.communityId] = room.communityId
    }.insertedCount > 0) {
        "create room failed"
    }
    check(Aids.insert {
        it[value] = room.aid
        it[objectId] = room.id
        it[objectType] = ObjectType.ROOM
    }.insertedCount > 0) {
        "create aid failed"
    }
    addRoomJoinRaw(room.id, room.creator, room.createdTime)
}

suspend fun ExposedDatabaseSession.getRoomRawResultList(
    objectListFetch: ObjectListFetch,
): Result<List<RoomRawResult>> {
    return dbSearch {
        search {
            Rooms
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value).where {
                    when (objectListFetch) {
                        is ObjectListFetch.AidListFetch -> Aids.value inList objectListFetch.aidList
                        is ObjectListFetch.IdListFetch -> Rooms.id inList objectListFetch.idList
                    }
                }
        }
        map {
            val joinedTime = it.getOrNull(MemberJoins.joinedTime)
            val topicId = it.getOrNull(UserTopicReads.topicId)
            val room = Room.Companion.wrapRow(it)
            RoomRawResult(room, room.icon, joinedTime, topicId, 0)
        }
    }
}

suspend fun ExposedDatabaseSession.getRoomList(
    objectListFetch: ObjectListFetch,
): Result<List<Room>> {
    return dbSearch {
        search {
            Rooms.join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value)
                .where {
                    when (objectListFetch) {
                        is ObjectListFetch.AidListFetch -> Aids.value inList objectListFetch.aidList
                        is ObjectListFetch.IdListFetch -> Rooms.id inList objectListFetch.idList
                    }
                }
        }
        map {
            Room.Companion.wrapRow(it)
        }
    }
}

@Suppress("MoveLambdaOutsideParentheses")
suspend fun ExposedDatabaseSession.updateRoom(
    id: PrimaryKey,
    body: UpdateRoomBody
) = dbQuery {
    listOf({
        val newIcon = body.icon
        val newName = body.name
        if (!newName.isNullOrBlank() || newIcon != null) {
            Rooms.update({
                Rooms.id eq id
            }) {
                if (!newName.isNullOrBlank()) {
                    it[Rooms.name] = newName
                }
                if (newIcon != null) {
                    it[Rooms.icon] = newIcon.ifEmpty { null }
                }
            } > 0
        } else {
            true
        }
    }).all {
        it()
    }
}

fun batchCreateCommunityRooms(rooms: List<Room>) {
    check(Rooms.batchInsert(rooms) {
        this[Rooms.id] = it.id
        this[Rooms.name] = it.name
        this[Rooms.communityId] = it.communityId
        this[Rooms.creator] = it.creator
        this[Rooms.createdTime] = it.createdTime
    }.size == rooms.size) {
        "insert room failed"
    }
    check(Aids.batchInsert(rooms) {
        this[Aids.value] = it.aid.take(AID_LENGTH)
        this[Aids.objectId] = it.id
        this[Aids.objectType] = ObjectType.ROOM
    }.size == rooms.size) {
        "insert room aid failed"
    }
    check(MemberJoins.batchInsert(rooms) {
        this[MemberJoins.uid] = it.creator
        this[MemberJoins.objectId] = it.id
        this[MemberJoins.joinedTime] = it.createdTime
        this[MemberJoins.objectType] = ObjectType.ROOM
    }.size == rooms.size) {
        "join room failed"
    }
}
