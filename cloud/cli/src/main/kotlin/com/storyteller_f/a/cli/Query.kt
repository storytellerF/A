package com.storyteller_f.a.cli

import com.storyteller_f.a.exposed.tables.Aids
import com.storyteller_f.a.exposed.tables.Communities
import com.storyteller_f.a.exposed.tables.Community
import com.storyteller_f.a.exposed.tables.MemberJoins
import com.storyteller_f.a.exposed.tables.MemberJoins.joinedTime
import com.storyteller_f.a.exposed.tables.MemberJoins.objectId
import com.storyteller_f.a.exposed.tables.MemberJoins.objectType
import com.storyteller_f.a.exposed.tables.MemberJoins.uid
import com.storyteller_f.a.exposed.tables.Room
import com.storyteller_f.a.exposed.tables.Rooms
import com.storyteller_f.a.exposed.tables.User
import com.storyteller_f.a.exposed.tables.Users
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.createCommunityRoomsRaw
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.batchInsert

suspend fun Backend.batchAddUser(users: List<User>): Result<List<ResultRow>> = databaseSession.dbQuery {
    Users.batchInsert(users) {
        this[Users.id] = it.id
        this[Users.icon] = it.icon
        this[Users.nickname] = it.nickname
        this[Users.publicKey] = it.publicKey
        this[Users.address] = it.address
        this[Users.createdTime] = it.createdTime
        this[Users.passType] = it.passType
    }
    Aids.batchInsert(users) {
        this[Aids.value] = it.aid!!
        this[Aids.objectId] = it.id
        this[Aids.objectType] = ObjectType.USER
    }
}

suspend fun Backend.batchAddCommunities(
    l2: List<Community>,
    memberList: List<Pair<PrimaryKey, List<PrimaryKey>>>,
    l3: List<Triple<PrimaryKey, PrimaryKey, String>>
): Result<Unit> = databaseSession.dbQuery {
    Communities.batchInsert(l2) {
        this[Communities.id] = it.id
        this[Communities.createdTime] = it.createdTime
        this[Communities.name] = it.name
        this[Communities.icon] = it.iconId
        this[Communities.owner] = it.owner
        this[Communities.fontId] = it.fontId
    }
    Aids.batchInsert(l2) {
        this[Aids.value] = it.aid
        this[Aids.objectId] = it.id
        this[Aids.objectType] = ObjectType.COMMUNITY
    }
    memberList.forEach { (communityId, uidList) ->
        MemberJoins.batchInsert(uidList) {
            this[joinedTime] = now()
            this[uid] = it
            this[objectId] = communityId
            this[objectType] = ObjectType.COMMUNITY
        }
    }
    l3.forEach { (c, communityId, aid) ->
        createCommunityRoomsRaw(communityId, c, aid)
    }
}

suspend fun Backend.batchAddRooms(
    roomList: List<Room>,
    membersList: List<Pair<List<PrimaryKey>, PrimaryKey>>
): Result<Unit> = databaseSession.dbQuery {
    Rooms.batchInsert(roomList) {
        this[Rooms.id] = it.id
        this[Rooms.icon] = it.icon
        this[Rooms.name] = it.name
        this[Rooms.communityId] = it.communityId
        this[Rooms.creator] = it.creator
        this[Rooms.createdTime] = it.createdTime
    }
    Aids.batchInsert(roomList) {
        this[Aids.value] = it.aid
        this[Aids.objectId] = it.id
        this[Aids.objectType] = ObjectType.ROOM
    }
    membersList.forEachIndexed { _, addRoom ->
        MemberJoins.batchInsert(addRoom.first) {
            this[uid] = it
            this[objectId] = addRoom.second
            this[joinedTime] = now()
            this[objectType] = ObjectType.ROOM
        }
    }
}

suspend fun Backend.getAllMembers(distinct: List<String>): Result<List<Triple<String, Long, String>>> =
    databaseSession.dbQuery {
        MemberJoins
            .join(Rooms, JoinType.INNER, objectId, Rooms.id)
            .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
            .join(Users, JoinType.INNER, uid, Users.id)
            .select(Users.fields + Aids.value)
            .where {
                Aids.value inList distinct
            }.map {
                Triple(it[Users.publicKey], it[Users.id], it[Aids.value])
            }
    }
