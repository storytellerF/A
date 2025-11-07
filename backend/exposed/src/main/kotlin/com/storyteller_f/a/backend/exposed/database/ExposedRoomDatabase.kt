package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.RoomDatabase
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildRoomSearchWhereQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.Members
import com.storyteller_f.a.backend.exposed.tables.Rooms
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.a.backend.exposed.tables.batchAddMembers
import com.storyteller_f.a.backend.exposed.tables.findRoomById
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedRoomDatabase(
    val databaseSession: ExposedDatabaseSession,
    val containerDatabase: ContainerDatabase,
) : RoomDatabase {
    override suspend fun checkRoomIsPrivate(roomId: PrimaryKey) = databaseSession.dbSearch {
        search {
            Room.findRoomById(roomId)
        }
        first {
            it[Rooms.communityId] == null
        }
    }

    override suspend fun getRoomPaginationResult(
        uid: PrimaryKey?,
        word: String?,
        community: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch,
    ) = databaseSession.dbSearch {
        search {
            Rooms
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .select(Rooms.fields + Aids.value)
                .buildRoomSearchWhereQuery(joinSearch, community, word)
                .bindPaginationQuery(Rooms, primaryKeyFetch)
        }
        map(Room::wrapRow)
    }.mapResult {
        databaseSession.dbSearch {
            search {
                Rooms.select(Rooms.id)
                    .buildRoomSearchWhereQuery(joinSearch, community, word)
            }
            count()
        }.mapResult { count ->
            processRoomListToRawRoom(uid, it).map { list ->
                PaginationResult(list, count)
            }
        }
    }

    override suspend fun getRoomCommunityId(parentId: PrimaryKey) = databaseSession.dbSearch {
        search {
            Room.findRoomById(parentId)
        }
        first {
            it[Rooms.communityId]
        }
    }

    override suspend fun getRoomPubKeyPaginationResult(
        roomId: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ) = runCatching {
        val list = databaseSession.dbSearch {
            search {
                Users.join(Members, JoinType.INNER, Users.id, Members.uid)
                    .select(Users.id, Users.publicKey).where {
                        Members.objectId eq roomId
                    }.bindPaginationQuery(Users, primaryKeyFetch)
            }
            map {
                UserPubKeyInfo(it[Users.id], it[Users.publicKey])
            }
        }.getOrThrow()
        val total = databaseSession.dbSearch {
            search {
                Users.join(Members, JoinType.INNER, Users.id, Members.uid)
                    .selectAll().where {
                        Members.objectId eq roomId
                    }
            }
            count()
        }.getOrThrow()
        PaginationResult(list, total)
    }

    override suspend fun getRawRoom(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean?,
        uid: PrimaryKey?,
    ): Result<RawRoom?> {
        if (uid == null && fillJoinInfo == true) return Result.failure(UnauthorizedException())
        return databaseSession.dbSearch {
            search {
                Rooms.join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
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
            processRoomListToRawRoom(uid, listOf(room))
        }.mapIfNotNull {
            it.first()
        }
    }

    suspend fun processRoomListToRawRoom(
        uid: PrimaryKey?,
        rooms: List<Room>,
    ): Result<List<RawRoom>> = containerDatabase.getContainerInfo(rooms.map {
        it.id
    }, uid).map { map ->
        rooms.map { room ->
            val containerInfo = map[room.id]
            RawRoom(
                room,
                containerInfo?.member?.joinedTime,
                containerInfo?.userTopicRead?.topicId,
                containerInfo?.memberCount,
                containerInfo?.latestTopicId,
            )
        }
    }

    override suspend fun createRoom(room: Room, members: List<Member>): Result<Room> =
        databaseSession.dbQuery {
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
            batchAddMembers(members)
            room
        }

    override suspend fun getRawRooms(objectListFetch: ObjectListFetch, uid: PrimaryKey?) =
        databaseSession.dbSearch {
            search {
                Rooms.join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                    .select(Rooms.fields + Aids.value).where {
                        when (objectListFetch) {
                            is ObjectListFetch.AidListFetch -> Aids.value inList objectListFetch.aidList
                            is ObjectListFetch.IdListFetch -> Rooms.id inList objectListFetch.idList
                        }
                    }
            }
            map {
                Room.wrapRow(it)
            }
        }.mapResult {
            processRoomListToRawRoom(uid, it)
        }

    override suspend fun getRoomList(objectListFetch: ObjectListFetch) = databaseSession.dbSearch {
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

    override suspend fun updateRoom(
        id: PrimaryKey,
        body: UpdateRoomBody,
    ) = databaseSession.dbQuery {
        listOf(suspend {
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
        }).all {
            it()
        }
    }

    override suspend fun getPrivateRoomCount() = databaseSession.dbSearch {
        search {
            Rooms.selectAll().where {
                Rooms.communityId.isNull()
            }
        }
        count()
    }

    override suspend fun getPublicRoomCount() = databaseSession.dbSearch {
        search {
            Rooms.selectAll().where {
                Rooms.communityId.isNotNull()
            }
        }
        count()
    }
}
