package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.types.PaginationResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement

object Rooms : BaseTable() {
    val name = roomName()
    val icon = roomIcon()
    val creator = customPrimaryKey("creator").index()
    val communityId = customPrimaryKey("community_id").index().nullable()
}

class Room(
    val aid: String,
    val name: String,
    val icon: String?,
    val creator: PrimaryKey,
    val communityId: PrimaryKey?,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Room {
            return Room(
                row[Aids.value],
                row[Rooms.name],
                row[Rooms.icon],
                row[Rooms.creator],
                row[Rooms.communityId],
                row[Rooms.id],
                row[Rooms.createdTime]
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

        fun new(room: Room): InsertStatement<Number> {
            return Rooms.insert { statement ->
                statement[id] = room.id
                statement[createdTime] = room.createdTime
                statement[name] = room.name
                statement[icon] = room.icon
                statement[creator] = room.creator
                statement[communityId] = room.communityId
            }
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

private fun Room.toRoomInfo(joinedTime: LocalDateTime?) = RoomInfo(
    id,
    name,
    aid,
    creator,
    null,
    createdTime,
    joinedTime,
    communityId
)

suspend fun commonPaginationRoomList(
    uid: PrimaryKey?,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?
): Result<Pair<List<Pair<RoomInfo, String?>>, Long>> {
    return DatabaseFactory.mapQuery(::mapRoomInfo) {
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

suspend fun getRoomCommunityId(parentId: PrimaryKey): Result<PrimaryKey?> = DatabaseFactory.first({
    it[Rooms.communityId]
}) {
    Room.findRoomById(parentId)
}

suspend fun commonPaginationRoomPubKeyList(
    roomId: PrimaryKey,
    pre: PrimaryKey?,
    next: PrimaryKey?,
    size: Int
): Result<Pair<List<Pair<Long, String>>, Long>> {
    return DatabaseFactory.mapQuery({
        this[Users.id] to this[Users.publicKey]
    }) {
        buildRoomPubKeyQuery(roomId, false).bindPaginationQuery(Users, pre, next, size)
    }.mapResult { data ->
        DatabaseFactory.count {
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

suspend fun getRoomSource(
    roomId: PrimaryKey?,
    roomAid: String?,
    fillJoinInfo: Boolean?,
    uid: PrimaryKey?
): Result<Pair<RoomInfo, String?>?> = DatabaseFactory.first(::mapRoomInfo) {
    val baseOp = Op.build {
        if (roomId != null) {
            Rooms.id eq roomId
        } else {
            Aids.value eq roomAid!!
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
    return commonPaginationRoomList(
        uid,
        preRoomId,
        nextRoomId,
        size,
        joinStatusSearch,
        word,
        community
    ).mapResult { (list, count) ->
        roomsResponse(list, backend).map { value ->
            PaginationResult(value, count)
        }
    }
}

private fun roomsResponse(list: List<Pair<RoomInfo, String?>>, backend: Backend): Result<List<RoomInfo>> {
    return backend.mediaService.get("amedia", list.map {
        it.second
    }).map { icons ->
        list.mapIndexed { i, roomPair ->
            roomPair.first.copy(icon = icons[i])
        }
    }
}

suspend fun createRoom(room4: Room): Result<Boolean> = DatabaseFactory.dbQuery {
    Room.new(room4).insertedCount > 0 && Aids.insert {
        it[value] = room4.aid
        it[objectId] = room4.id
        it[objectType] = ObjectType.ROOM
    }.insertedCount > 0
}
