package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
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

suspend fun DatabaseFactory.checkRoomIsPrivate(backend: Backend, roomId: PrimaryKey): Result<Boolean?> {
    return dbSearch(backend) {
        search {
            Room.findRoomById(roomId)
        }
        first {
            it[Rooms.communityId] == null
        }
    }
}

fun mapRoomInfo(it: ResultRow): Pair<RoomInfo, String?> {
    val joinedTime = it.getOrNull(MemberJoins.joinedTime)
    val topicId = it.getOrNull(UserTopicReads.topicId)
    val room = Room.wrapRow(it)
    return room.toRoomInfo(0, joinedTime, topicId) to room.icon
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

suspend fun getRoomPaginationList(
    backend: Backend,
    uid: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?,
    pagingFetch: PagingFetch
): Result<Pair<List<Pair<RoomInfo, String?>>, Long>> {
    val joinSearch = joinStatusSearch.toJoinSearch(uid)
    return DatabaseFactory.dbSearch(backend) {
        search {
            Rooms
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value)
                .buildRoomSearchWhereQuery(joinSearch, community, word)
                .bindPaginationQuery(Rooms, pagingFetch)
        }
        map(Room::wrapRow)
    }.mapResult {
        processRoomInfo(uid, backend, it).mapResult { list ->
            DatabaseFactory.count(backend) {
                Rooms.select(Rooms.id).buildRoomSearchWhereQuery(joinSearch, community, word)
            }.map { count ->
                list to count
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

suspend fun DatabaseFactory.getRoomCommunityId(backend: Backend, parentId: PrimaryKey): Result<PrimaryKey?> =
    dbSearch(backend) {
        search {
            Room.findRoomById(parentId)
        }
        first {
            it[Rooms.communityId]
        }
    }

suspend fun DatabaseFactory.commonPaginationRoomPubKeyList(
    backend: Backend,
    roomId: PrimaryKey,
    pagingFetch: PagingFetch
): Result<Pair<List<Pair<Long, String>>, Long>> {
    return dbSearch(backend) {
        search {
            buildRoomPubKeyQuery(roomId, false).bindPaginationQuery(Users, pagingFetch)
        }
        map {
            it[Users.id] to it[Users.publicKey]
        }
    }.mapResult { data ->
        count(backend) {
            buildRoomPubKeyQuery(roomId, true)
        }.map { value ->
            data to value
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

suspend fun DatabaseFactory.getRoom(
    backend: Backend,
    objectFetch: ObjectFetch,
    fillJoinInfo: Boolean? = null,
    uid: PrimaryKey? = null,
): Result<Pair<RoomInfo, String?>?> {
    if (uid == null && fillJoinInfo == true) return Result.failure(UnauthorizedException())
    return dbSearch(backend) {
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
        processRoomInfo(uid, backend, listOf(room)).map {
            it.first()
        }
    }
}

private suspend fun processRoomInfo(
    uid: PrimaryKey?,
    backend: Backend,
    rooms: List<Room>
): Result<List<Pair<RoomInfo, String?>>> = DatabaseFactory.getContainerInfo(backend, rooms.map {
    it.id
}, uid).map { (joinedTimeMap, lastReadMap, memberCountMap) ->
    rooms.map { room ->
        room.toRoomInfo(
            memberCountMap[room.id] ?: 0,
            joinedTimeMap[room.id]?.joinedTime,
            lastReadMap[room.id]?.topicId
        ) to room.icon
    }
}

suspend fun searchRooms(
    backend: Backend,
    uid: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?,
    pagingFetch: PagingFetch
): Result<PaginationResult<RoomInfo>?> {
    return getRoomPaginationList(
        backend,
        uid,
        joinStatusSearch,
        word,
        community,
        pagingFetch
    ).mapResult { (list, count) ->
        processRoomList(list, backend).mapIfNotNull { value ->
            PaginationResult(value, count)
        }
    }
}

suspend fun processRoomList(list: List<Pair<RoomInfo, String?>>, backend: Backend): Result<List<RoomInfo>?> {
    return DatabaseFactory.getMediaInfoList(backend, list.map {
        it.second
    }).mapIfNotNull { icons ->
        list.mapIndexed { i, roomPair ->
            roomPair.first.copy(icon = icons[i])
        }
    }
}

suspend fun DatabaseFactory.createRoom(backend: Backend, room: Room) = dbQuery(backend) {
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

suspend fun DatabaseFactory.getRoomByIds(
    backend: Backend,
    ids: List<PrimaryKey>
): Result<List<Pair<RoomInfo, String?>>> {
    return dbSearch(backend) {
        search {
            Rooms
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value).where {
                    Rooms.id inList ids
                }
        }
        map {
            mapRoomInfo(it)
        }
    }
}

suspend fun DatabaseFactory.getRoomByAids(
    backend: Backend,
    aids: List<String>
): Result<List<Room>> {
    return dbSearch(backend) {
        search {
            Rooms.join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value)
                .where {
                    Aids.value inList aids
                }
        }
        map {
            Room.wrapRow(it)
        }
    }
}

@Suppress("MoveLambdaOutsideParentheses")
suspend fun DatabaseFactory.updateRoom(
    backend: Backend,
    id: PrimaryKey,
    body: UpdateRoomBody
) = dbQuery(backend) {
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
