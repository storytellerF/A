package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.*
import com.storyteller_f.a.backend.exposed.query.PaginationResult
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildRoomPubKeyQuery
import com.storyteller_f.a.backend.exposed.query.buildRoomSearchWhereQuery
import com.storyteller_f.a.backend.exposed.tables.*
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update

class ExposedRoomDatabase(
    val exposedDatabaseSession: ExposedDatabaseSession,
    val containerDatabase: ContainerDatabase,
) :
    RoomDatabase {
    override suspend fun checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Room.Companion.findRoomById(roomId)
            }
            first {
                it[Rooms.communityId] == null
            }
        }
    }

    override suspend fun getRoomPaginationResult(
        uid: PrimaryKey?,
        word: String?,
        community: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch,
    ): Result<PaginationResult<RawRoom>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Rooms
                    .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                    .select(Rooms.fields + Aids.value)
                    .buildRoomSearchWhereQuery(joinSearch, community, word)
                    .bindPaginationQuery(Rooms, primaryKeyFetch)
            }
            map(Room::wrapRow)
        }.mapResult {
            processRoomListToRawRoom(uid, it).mapResult { list ->
                exposedDatabaseSession.dbSearch {
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

    override suspend fun getRoomCommunityId(parentId: PrimaryKey): Result<PrimaryKey?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Room.Companion.findRoomById(parentId)
            }
            first {
                it[Rooms.communityId]
            }
        }
    }

    override suspend fun getRoomPubKeyPaginationResult(
        roomId: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<UserPubKeyInfo>> {
        return exposedDatabaseSession.dbSearch {
            search {
                buildRoomPubKeyQuery(roomId, false).bindPaginationQuery(Users, primaryKeyFetch)
            }
            map {
                UserPubKeyInfo(it[Users.id], it[Users.publicKey])
            }
        }.mapResult { data ->
            exposedDatabaseSession.dbSearch {
                search {
                    buildRoomPubKeyQuery(roomId, true)
                }
                count()
            }.map { value ->
                PaginationResult(data, value)
            }
        }
    }

    override suspend fun getRawRoom(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean?,
        uid: PrimaryKey?,
    ): Result<RawRoom?> {
        if (uid == null && fillJoinInfo == true) return Result.failure(UnauthorizedException())
        return exposedDatabaseSession.dbSearch {
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
            processRoomListToRawRoom(uid, listOf(room)).map {
                it.first()
            }
        }
    }

    override suspend fun processRoomListToRawRoom(
        uid: PrimaryKey?,
        rooms: List<Room>,
    ): Result<List<RawRoom>> = containerDatabase.getContainerInfo(rooms.map {
        it.id
    }, uid).map { (joinedTimeMap, lastReadMap, memberCountMap) ->
        rooms.map { room ->
            RawRoom(
                room,
                joinedTimeMap[room.id]?.joinedTime,
                lastReadMap[room.id]?.topicId,
                memberCountMap[room.id] ?: 0,
            )
        }
    }

    override suspend fun createRoom(room: Room): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
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
            MemberJoin.addJoinRaw(room.creator, room.id, room.createdTime, ObjectType.ROOM)
        }
    }

    override suspend fun getRawRooms(
        objectListFetch: ObjectListFetch,
    ): Result<List<RawRoom>> {
        return exposedDatabaseSession.dbSearch {
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
                val room = Room.wrapRow(it)
                RawRoom(room, joinedTime, topicId, 0)
            }
        }
    }

    override suspend fun getRoomList(
        objectListFetch: ObjectListFetch,
    ): Result<List<Room>> {
        return exposedDatabaseSession.dbSearch {
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

    override suspend fun updateRoom(
        id: PrimaryKey,
        body: UpdateRoomBody,
    ): Result<Boolean> {
        return exposedDatabaseSession.dbQuery {
            listOf {
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
                            it[Rooms.icon] = newIcon
                        }
                    } > 0
                } else {
                    true
                }
            }.all {
                it()
            }
        }
    }
}
