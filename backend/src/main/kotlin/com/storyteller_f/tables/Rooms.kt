package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PrimaryKeyFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Rooms : BaseTable() {
    val name = roomName()
    val icon = roomIcon()
    val creator = customPrimaryKey("creator").index()
    val communityId = customPrimaryKey("community_id").index().nullable()
}

class Room(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val creator: PrimaryKey,
    val icon: String? = null,
    val communityId: PrimaryKey? = null,
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Room {
            return with(Rooms) {
                Room(
                    row[id],
                    row[createdTime],
                    row[Aids.value],
                    row[name],
                    row[creator],
                    row[icon],
                    row[communityId]
                )
            }
        }

        fun findRoomById(id: PrimaryKey) = Rooms.selectAll().where {
            Rooms.id eq id
        }
    }
}

suspend fun Backend.checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?> {
    return databaseSession.dbSearch {
        search {
            Room.findRoomById(roomId)
        }
        first {
            it[Rooms.communityId] == null
        }
    }
}

fun mapRoomInfo(it: ResultRow): RoomRawResult {
    val joinedTime = it.getOrNull(MemberJoins.joinedTime)
    val topicId = it.getOrNull(UserTopicReads.topicId)
    val room = Room.wrapRow(it)
    return RoomRawResult(room.toRoomInfo(0, joinedTime, topicId), room.icon)
}

fun Room.toRoomInfo(memberCount: Long, joinedTime: LocalDateTime?, topicId: Long?) = RoomInfo(
    id,
    createdTime,
    name,
    aid,
    creator,
    memberCount,
    joinedTime = joinedTime,
    communityId = communityId,
    lastRead = topicId
)

suspend fun Backend.getRoomPaginationResult(
    uid: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<RoomRawResult>> {
    val joinSearch = joinStatusSearch.toJoinSearch(uid)
    return databaseSession.dbSearch {
        search {
            Rooms
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value)
                .buildRoomSearchWhereQuery(joinSearch, community, word)
                .bindPaginationQuery(Rooms, primaryKeyFetch)
        }
        map(Room::wrapRow)
    }.mapResult {
        processRoomListToRoomRawResult(uid, it).mapResult { list ->
            databaseSession.dbSearch {
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

suspend fun Backend.getRoomCommunityId(parentId: PrimaryKey): Result<PrimaryKey?> =
    databaseSession.dbSearch {
        search {
            Room.findRoomById(parentId)
        }
        first {
            it[Rooms.communityId]
        }
    }

suspend fun Backend.getRoomPubKeyPaginationResult(
    roomId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<UserPubKeyInfo>> {
    return databaseSession.dbSearch {
        search {
            buildRoomPubKeyQuery(roomId, false).bindPaginationQuery(Users, primaryKeyFetch)
        }
        map {
            UserPubKeyInfo(it[Users.id], it[Users.publicKey])
        }
    }.mapResult { data ->
        databaseSession.dbSearch {
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

suspend fun Backend.getRoomRawResult(
    objectFetch: ObjectFetch,
    fillJoinInfo: Boolean? = null,
    uid: PrimaryKey? = null,
): Result<RoomRawResult?> {
    if (uid == null && fillJoinInfo == true) return Result.failure(UnauthorizedException())
    return databaseSession.dbSearch {
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
        first(Room::wrapRow)
    }.mapResultIfNotNull { room ->
        processRoomListToRoomRawResult(uid, listOf(room)).map {
            it.first()
        }
    }
}

private suspend fun Backend.processRoomListToRoomRawResult(
    uid: PrimaryKey?,
    rooms: List<Room>
): Result<List<RoomRawResult>> = getContainerInfo(rooms.map {
    it.id
}, uid).map { (joinedTimeMap, lastReadMap, memberCountMap) ->
    rooms.map { room ->
        RoomRawResult(
            room.toRoomInfo(
                memberCountMap[room.id] ?: 0,
                joinedTimeMap[room.id]?.joinedTime,
                lastReadMap[room.id]?.topicId
            ),
            room.icon
        )
    }
}

suspend fun Backend.searchRoomPaginationResult(
    uid: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<RoomInfo>?> {
    return getRoomPaginationResult(
        uid,
        joinStatusSearch,
        word,
        community,
        primaryKeyFetch
    ).mapResult { (list, count) ->
        processRoomRawResultToRoomInfo(list).mapIfNotNull { value ->
            PaginationResult(value, count)
        }
    }
}

suspend fun Backend.processRoomRawResultToRoomInfo(list: List<RoomRawResult>): Result<List<RoomInfo>?> {
    return getMediaInfoList(list.map {
        it.icon
    }).mapIfNotNull { icons ->
        list.mapIndexed { i, roomPair ->
            roomPair.roomInfo.copy(icon = icons[i])
        }
    }
}

suspend fun Backend.createRoom(room: Room) = databaseSession.dbQuery {
    check(Rooms.insert { statement ->
        statement[id] = room.id
        statement[createdTime] = room.createdTime
        statement[name] = room.name
        statement[icon] = room.icon
        statement[creator] = room.creator
        statement[communityId] = room.communityId
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

data class RoomRawResult(val roomInfo: RoomInfo, val icon: String?)

suspend fun Backend.getRoomRawResultList(
    objectListFetch: ObjectListFetch,
): Result<List<RoomRawResult>> {
    return databaseSession.dbSearch {
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
            mapRoomInfo(it)
        }
    }
}

suspend fun Backend.getRoomByAids(
    objectListFetch: ObjectListFetch,
): Result<List<Room>> {
    return databaseSession.dbSearch {
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
            Room.wrapRow(it)
        }
    }
}

@Suppress("MoveLambdaOutsideParentheses")
suspend fun Backend.updateRoom(
    id: PrimaryKey,
    body: UpdateRoomBody
) = databaseSession.dbQuery {
    listOf({
        val newIcon = body.icon
        val newName = body.name
        if (!newName.isNullOrBlank() || newIcon != null) {
            Rooms.update({
                Rooms.id eq id
            }) {
                if (!newName.isNullOrBlank()) {
                    it[name] = newName
                }
                if (newIcon != null) {
                    it[icon] = newIcon.ifEmpty { null }
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
