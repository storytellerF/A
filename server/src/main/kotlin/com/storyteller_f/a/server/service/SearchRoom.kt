package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.UnauthorizedException
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.MemberJoins
import com.storyteller_f.tables.Rooms
import com.storyteller_f.types.PaginationResult
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

suspend fun searchRooms(
    uid: PrimaryKey?,
    backend: Backend,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int,
    search: RouteRooms.Search,
): Result<PaginationResult<RoomInfo>?> {
    return DatabaseFactory.mapQuery(::mapRoomInfo) {
        buildRoomSearchQuery(search, uid, false).bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
    }.mapResult { list ->
        DatabaseFactory.count {
            buildRoomSearchQuery(search, uid, true)
        }.mapResult { count ->
            roomsResponse(list, backend).map { value ->
                PaginationResult(value, count)
            }
        }
    }
}

private fun buildRoomSearchQuery(
    search: RouteRooms.Search,
    uid: PrimaryKey?,
    getCount: Boolean
): Query {
    val joinStatusSearch = search.joinStatus
    val query = when (joinStatusSearch) {
        JoinStatusSearch.JOINED -> buildJoinedRoomSearchQuery(uid, getCount)
        JoinStatusSearch.NOT_JOINED -> buildNotJoinedRoomSearchQuery(uid)
        else -> buildUnspecifiedRoomSearchQuery(uid, getCount)
    }
    if (!(search.word.isNullOrBlank())) {
        query.andWhere {
            Rooms.name like "%${search.word}%"
        }
    }
    if (search.community != null) {
        query.andWhere {
            Rooms.communityId eq search.community
        }
    }
    return query
}

private fun buildUnspecifiedRoomSearchQuery(
    uid: PrimaryKey?,
    getCount: Boolean
): Query = if (uid != null) {
    val join = Rooms
        .join(MemberJoins, JoinType.LEFT, Rooms.id, MemberJoins.objectId) {
            (MemberJoins.uid eq uid).or(MemberJoins.uid.isNull())
        }
    if (getCount) {
        join.selectAll()
    } else {
        join.select(Rooms.fields + MemberJoins.joinTime)
    }
} else {
    Rooms
        .selectAll()
        .where {
            Rooms.communityId.isNotNull()
        }
}

private fun buildNotJoinedRoomSearchQuery(
    uid: PrimaryKey?
): Query = if (uid != null) {
    val join = Rooms
    join.selectAll().where {
        Rooms.id notInSubQuery (MemberJoins.select(MemberJoins.objectId).where {
            MemberJoins.uid eq uid
        })
    }
} else {
    throw UnauthorizedException()
}

private fun buildJoinedRoomSearchQuery(
    uid: PrimaryKey?,
    getCount: Boolean
): Query = if (uid != null) {
    val join = Rooms
        .join(MemberJoins, JoinType.INNER, Rooms.id, MemberJoins.objectId) {
            MemberJoins.uid eq uid
        }
    if (getCount) {
        join.selectAll()
    } else {
        join.select(Rooms.fields + MemberJoins.joinTime)
    }
} else {
    throw UnauthorizedException()
}

private fun roomsResponse(list: List<Pair<RoomInfo, String?>>, backend: Backend): Result<List<RoomInfo>> {
    return backend.mediaService.get("apic", list.map {
        it.second
    }).map { icons ->
        list.mapIndexed { i, roomPair ->
            roomPair.first.copy(icon = getMediaInfo(icons[i]))
        }
    }
}
