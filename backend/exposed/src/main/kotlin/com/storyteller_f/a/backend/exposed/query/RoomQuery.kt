package com.storyteller_f.a.backend.exposed.query

import com.storyteller_f.a.backend.core.AID_LENGTH
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.MemberJoins
import com.storyteller_f.a.backend.exposed.tables.Rooms
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll

fun Query.buildRoomSearchWhereQuery(
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

suspend fun batchCreateCommunityRooms(rooms: List<Room>) {
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
