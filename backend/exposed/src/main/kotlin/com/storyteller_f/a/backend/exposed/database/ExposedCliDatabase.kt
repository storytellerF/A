package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.CliDatabase
import com.storyteller_f.a.backend.core.InsertTopicTuple
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.Communities
import com.storyteller_f.a.backend.exposed.tables.EncryptedKeys
import com.storyteller_f.a.backend.exposed.tables.MemberJoins
import com.storyteller_f.a.backend.exposed.tables.MemberJoins.joinedTime
import com.storyteller_f.a.backend.exposed.tables.MemberJoins.objectId
import com.storyteller_f.a.backend.exposed.tables.MemberJoins.uid
import com.storyteller_f.a.backend.exposed.tables.Rooms
import com.storyteller_f.a.backend.exposed.tables.Topics
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.select
import kotlin.collections.map

class ExposedCliDatabase(val databaseSession: ExposedDatabaseSession) : CliDatabase {
    override suspend fun batchAddUser(users: List<User>) {
        databaseSession.dbQuery {
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
        }.getOrThrow()
    }

    override suspend fun batchAddCommunities(
        communities: List<Community>,
        memberList: List<Pair<PrimaryKey, List<PrimaryKey>>>,
    ) {
        databaseSession.dbQuery {
            Communities.batchInsert(communities) {
                this[Communities.id] = it.id
                this[Communities.createdTime] = it.createdTime
                this[Communities.name] = it.name
                this[Communities.icon] = it.iconId
                this[Communities.owner] = it.owner
                this[Communities.fontId] = it.fontId
            }
            Aids.batchInsert(communities) {
                this[Aids.value] = it.aid
                this[Aids.objectId] = it.id
                this[Aids.objectType] = ObjectType.COMMUNITY
            }
            memberList.forEach { (communityId, uidList) ->
                MemberJoins.batchInsert(uidList) {
                    this[joinedTime] = now()
                    this[uid] = it
                    this[objectId] = communityId
                    this[MemberJoins.objectType] = ObjectType.COMMUNITY
                }
            }
        }.getOrThrow()
    }

    override suspend fun batchAddRooms(
        roomList: List<Room>,
        membersList: List<Pair<List<PrimaryKey>, PrimaryKey>>
    ) {
        databaseSession.dbQuery {
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
                    this[MemberJoins.objectType] = ObjectType.ROOM
                }
            }
        }.getOrThrow()
    }

    override suspend fun getAllMembers(distinct: List<String>): Result<List<Triple<String, Long, String>>> {
        return databaseSession.dbQuery {
            MemberJoins
                .join(Rooms, JoinType.INNER, objectId, Rooms.id)
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .join(Users, JoinType.INNER, uid, Users.id)
                .select(Users.fields + Aids.value)
                .where {
                    Aids.value inList distinct
                }.toList().map {
                    Triple(it[Users.publicKey], it[Users.id], it[Aids.value])
                }
        }
    }

    override suspend fun batchAddEncryptTopics(
        tuples: List<InsertTopicTuple>,
        userMap: Map<String, User>,
        roomMap: Map<String, Room>,
        encryptedKeys: List<Triple<PrimaryKey, ByteArray, Long>>
    ) {
        databaseSession.dbQuery {
            insertTopics(tuples, userMap, ObjectType.ROOM)
            EncryptedKeys.batchInsert(encryptedKeys) { (topicId, b, uid) ->
                this[EncryptedKeys.topicId] = topicId
                this[EncryptedKeys.encryptedAes] = ExposedBlob(b)
                this[EncryptedKeys.uid] = uid
            }
        }.getOrThrow()
    }

    override suspend fun batchAddTopics(
        tuples: List<InsertTopicTuple>,
        userMap: Map<String, User>,
        objectType: ObjectType
    ) {
        databaseSession.dbQuery {
            insertTopics(tuples, userMap, objectType)
        }
    }

    private suspend fun insertTopics(
        topicTuples: List<InsertTopicTuple>,
        userMap: Map<String, User>,
        rootType: ObjectType,
    ) {
        Topics.batchInsert(topicTuples) {
            val presetTopic = it.topic
            val id = it.id
            val level = it.level
            val index = it.originalIndex
            this[Topics.id] = id
            this[Topics.author] = userMap[presetTopic.author]!!.id
            this[Topics.createdTime] = now()
            this[Topics.rootId] = it.rootId
            this[Topics.rootType] = rootType
            this[Topics.parentId] =
                if (level == 0) it.rootId else topicTuples[index - presetTopic.parent!!].id
            this[Topics.parentType] = if (level == 0) rootType else ObjectType.TOPIC
            this[Topics.content] = ExposedBlob(it.content)
            this[Topics.isEncrypted] = it.isEncrypted
            this[Topics.level] = level + 1
        }
        Aids.batchInsert(topicTuples.filter {
            !it.topic.aid.isNullOrBlank()
        }) {
            val first = it.topic
            val id = it.id
            this[Aids.value] = first.aid!!
            this[Aids.objectId] = id
            this[Aids.objectType] = ObjectType.TOPIC
        }
    }
}
