package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.types.PaginationResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Rooms : BaseTable() {
    val name = roomName()
    val icon = roomIcon()
    val creator = customPrimaryKey("creator").index()
    val communityId = customPrimaryKey("community_id").index().nullable()
    val memberCount = long("member_count")
}

class Room(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val creator: PrimaryKey,
    val memberCount: Long,
    val icon: String? = null,
    val communityId: PrimaryKey? = null
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Room {
            return Room(
                row[Rooms.id],
                row[Rooms.createdTime],
                row[Aids.value],
                row[Rooms.name],
                row[Rooms.creator],
                row[Rooms.memberCount],
                row[Rooms.icon],
                row[Rooms.communityId]
            )
        }

        fun findRoomById(id: PrimaryKey) = Rooms.selectAll().where {
            Rooms.id eq id
        }

        fun findRoomByAId(aid: String) = Rooms
            .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
            .select(Rooms.fields + Aids.value).where {
                Aids.value eq aid
            }
    }
}

suspend fun checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?> {
    return DatabaseFactory.first({
        it[Rooms.communityId] == null
    }) {
        Room.findRoomById(roomId)
    }
}

fun mapRoomInfo(it: ResultRow): Pair<RoomInfo, String?> {
    val joinedTime = it.getOrNull(MemberJoins.joinTime)
    val room = Room.wrapRow(it)
    return room.toRoomInfo(joinedTime) to room.icon
}

fun Room.toRoomInfo(joinedTime: LocalDateTime?) = RoomInfo(
    id,
    createdTime,
    name,
    aid,
    creator,
    memberCount,
    joinedTime = joinedTime,
    communityId = communityId
)

suspend fun getRoomPaginationList(
    uid: PrimaryKey?,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?
): Result<Pair<List<Pair<RoomInfo, String?>>, Long>> {
    return DatabaseFactory.mapQuery({
        mapRoomInfo(this)
    }) {
        buildRoomSearchQuery(uid, false, joinStatusSearch, word, community).bindPaginationQuery(
            Rooms,
            preRoomId,
            nextRoomId,
            size
        )
    }.mapResult { list ->
        DatabaseFactory.count {
            buildRoomSearchQuery(uid, true, joinStatusSearch, word, community)
        }.map { value ->
            list to value
        }
    }
}

private fun buildRoomSearchQuery(
    uid: PrimaryKey?,
    getCount: Boolean,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?
): Query {
    val query = when (joinStatusSearch) {
        JoinStatusSearch.JOINED -> buildJoinedRoomSearchQuery(uid, getCount)
        JoinStatusSearch.NOT_JOINED -> buildNotJoinedRoomSearchQuery(uid)
        else -> buildUnspecifiedRoomSearchQuery(uid, getCount)
    }
    if (!(word.isNullOrBlank())) {
        query.andWhere {
            Rooms.name like "%$word%"
        }
    }
    if (community != null) {
        query.andWhere {
            Rooms.communityId eq community
        }
    }
    return query
}

private fun buildUnspecifiedRoomSearchQuery(
    uid: PrimaryKey?,
    getCount: Boolean
): Query = if (uid != null) {
    val join = Rooms
        .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
        .join(MemberJoins, JoinType.LEFT, Rooms.id, MemberJoins.objectId) {
            (MemberJoins.uid eq uid)
        }
    if (getCount) {
        join.selectAll()
    } else {
        join.select(Rooms.fields + MemberJoins.joinTime + Aids.value)
    }.where {
        (MemberJoins.uid.isNull() and Rooms.communityId.isNotNull()).or(MemberJoins.uid.isNotNull())
    }
} else {
    Rooms
        .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
        .select(Rooms.fields + Aids.value)
        .where {
            Rooms.communityId.isNotNull()
        }
}

private fun buildNotJoinedRoomSearchQuery(
    uid: PrimaryKey?
): Query = if (uid != null) {
    Rooms.join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
        .select(Rooms.fields + Aids.value)
        .where {
            Rooms.id notInSubQuery (MemberJoins.select(MemberJoins.objectId).where {
                MemberJoins.uid eq uid
            }) and Rooms.communityId.isNotNull()
        }
} else {
    throw UnauthorizedException()
}

private fun buildJoinedRoomSearchQuery(
    uid: PrimaryKey?,
    getCount: Boolean
): Query = if (uid != null) {
    val join = Rooms
        .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
        .join(MemberJoins, JoinType.INNER, Rooms.id, MemberJoins.objectId) {
            MemberJoins.uid eq uid
        }

    if (getCount) {
        join.selectAll()
    } else {
        join.select(Rooms.fields + MemberJoins.joinTime + Aids.value)
    }
} else {
    throw UnauthorizedException()
}

suspend fun DatabaseFactory.getRoomCommunityId(parentId: PrimaryKey): Result<PrimaryKey?> = first({
    it[Rooms.communityId]
}) {
    Room.findRoomById(parentId)
}

suspend fun DatabaseFactory.commonPaginationRoomPubKeyList(
    roomId: PrimaryKey,
    pre: PrimaryKey?,
    next: PrimaryKey?,
    size: Int
): Result<Pair<List<Pair<Long, String>>, Long>> {
    return mapQuery({
        this[Users.id] to this[Users.publicKey]
    }) {
        buildRoomPubKeyQuery(roomId, false).bindPaginationQuery(Users, pre, next, size)
    }.mapResult { data ->
        count {
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

suspend fun DatabaseFactory.getRoomSource(
    roomId: PrimaryKey?,
    roomAid: String? = null,
    fillJoinInfo: Boolean? = null,
    uid: PrimaryKey? = null,
) = first({
    mapRoomInfo(it)
}) {
    val baseOp = Op.build {
        if (roomId != null) {
            Rooms.id eq roomId
        } else if (roomAid != null) {
            Aids.value eq roomAid
        } else {
            throw CustomBadRequestException("must be specify id or aid")
        }
    }

    when {
        fillJoinInfo != true -> Rooms
            .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
            .select(Rooms.fields + Aids.value)
            .where {
                baseOp and (Rooms.communityId.isNotNull())
            }

        uid != null -> Rooms
            .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
            .join(MemberJoins, JoinType.LEFT, Rooms.id, MemberJoins.objectId) {
                MemberJoins.uid eq uid
            }
            .select(Rooms.fields + MemberJoins.joinTime + Aids.value)
            .where {
                baseOp
            }

        else -> throw UnauthorizedException()
    }
}

suspend fun searchRooms(
    uid: PrimaryKey?,
    backend: Backend,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?
): Result<PaginationResult<RoomInfo>?> {
    return getRoomPaginationList(
        uid,
        preRoomId,
        nextRoomId,
        size,
        joinStatusSearch,
        word,
        community
    ).mapResult { (list, count) ->
        processRoomList(list, backend).map { value ->
            PaginationResult(value, count)
        }
    }
}

suspend fun processRoomList(list: List<Pair<RoomInfo, String?>>, backend: Backend): Result<List<RoomInfo>> {
    return backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, list.map {
        it.second
    }).map { icons ->
        list.mapIndexed { i, roomPair ->
            roomPair.first.copy(icon = icons[i])
        }
    }
}

suspend fun DatabaseFactory.createRoom(room: Room) = dbQuery {
    check(Rooms.insert { statement ->
        statement[id] = room.id
        statement[createdTime] = room.createdTime
        statement[name] = room.name
        statement[icon] = room.icon
        statement[creator] = room.creator
        statement[communityId] = room.communityId
        statement[memberCount] = room.memberCount
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
    addRoomJoinRaw(room.id, room.creator, room.createdTime, room.memberCount)
}

suspend fun DatabaseFactory.getRoomByIds(ids: List<PrimaryKey>): Result<List<Pair<RoomInfo, String?>>> {
    return mapQuery({ this }, { mapRoomInfo(it) }) {
        Rooms
            .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
            .select(Rooms.fields + Aids.value).where {
                Rooms.id inList ids
            }
    }
}

@Suppress("MoveLambdaOutsideParentheses")
suspend fun DatabaseFactory.updateRoom(
    id: PrimaryKey,
    body: UpdateRoomBody
) = dbQuery {
    listOf({
        val newIcon = body.icon
        val newName = body.name
        if (!newName.isNullOrBlank() || !newIcon.isNullOrBlank()) {
            Rooms.update({
                Rooms.id eq id
            }) {
                if (!newName.isNullOrBlank()) {
                    it[name] = newName
                }
                if (!newIcon.isNullOrBlank()) {
                    it[icon] = newIcon
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
        this[Rooms.memberCount] = it.memberCount
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
        this[MemberJoins.joinTime] = it.createdTime
        this[MemberJoins.objectType] = ObjectType.ROOM
    }.size == rooms.size) {
        "join room failed"
    }
}
