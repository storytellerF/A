package com.storyteller_f.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ROOM_ID_LENGTH
import com.storyteller_f.crypto_jvm.addProviderForJvm
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.media.AMEDIA_BUCKET
import com.storyteller_f.media.UploadPack
import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.PresetCommunity
import com.storyteller_f.shared.obj.PresetTopic
import com.storyteller_f.shared.obj.PresetValue
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.io.File
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
class AddPreset : Subcommand("add", "add entry") {
    private val jsonFilePath by argument(ArgType.String, "json", "json data file path")

    override fun execute() {
        if (!File(jsonFilePath).exists()) {
            Napier.i {
                "$jsonFilePath not exists."
            }
            exitProcess(1)
        }
        addProviderForJvm()
        DatabaseFactory.init(backend.config.databaseConnection)
        Napier.i {
            "database init done."
        }
        val jsonFile = File(jsonFilePath)

        val presetValue =
            ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<PresetValue>(jsonFile.readText())
        val parentDir = jsonFile.parentFile.canonicalFile
        runBlocking {
            try {
                when (val type = presetValue.type) {
                    "community" -> addCommunity(presetValue, parentDir)
                    "user" -> addUsers(presetValue, parentDir)
                    "topic" -> addTopics(presetValue, parentDir)
                    "room" -> addRooms(presetValue, parentDir)
                    else -> {
                        println("unrecognized type $type")
                        exitProcess(2)
                    }
                }
                Napier.i {
                    "add done $jsonFilePath."
                }
            } catch (e: Exception) {
                Napier.i(e) {
                    "exception when add"
                }
            }
        }
    }

    private suspend fun addRooms(presetValue: PresetValue, parentDir: File?) {
        val l = presetValue.roomData ?: return
        Napier.i {
            "rooms count ${presetValue.roomData?.size}"
        }
        val data = l.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            if (icon == null) {
                Triple(it, null, id)
            } else {
                val path = File(parentDir, icon)
                val p = "$id/room-icon.${path.extension}"
                backend.mediaService.upload(AMEDIA_BUCKET, listOf(UploadPack(p, path)))
                Triple(it, p, id)
            }
        }
        DatabaseFactory.dbQuery {
            val userMap = l.flatMap {
                it.users + it.admin
            }.distinct().map {
                User.wrapRow(findUserByAId(it).first())
            }.associateBy { it.aid }
            val communityMap = l.mapNotNull {
                it.community
            }.distinct().map {
                Community.wrapRow(findCommunityByAId(it).first())
            }.associateBy { it.aid }
            val idList = Rooms.batchInsert(data) { (it, p, id) ->
                this[Rooms.id] = id
                this[Rooms.icon] = p
                this[Rooms.name] = it.name
                this[Rooms.communityId] = communityMap[it.community]?.id
                this[Rooms.creator] = userMap[it.admin]!!.id
                this[Rooms.createdTime] = now()
            }.map {
                it[Rooms.id]
            }
            Aids.batchInsert(data) { (it, _, id) ->
                this[Aids.value] = it.id
                this[Aids.objectId] = id
                this[Aids.objectType] = ObjectType.ROOM
            }
            l.forEachIndexed { index, addRoom ->
                MemberJoins.batchInsert(addRoom.users) {
                    this[MemberJoins.uid] = userMap[it]!!.id
                    this[MemberJoins.objectId] = idList[index]
                    this[MemberJoins.joinTime] = now()
                    this[MemberJoins.objectType] = ObjectType.ROOM
                }
            }
        }.getOrThrow()
    }

    private suspend fun addTopics(presetValue: PresetValue, parentDir: File) {
        Napier.i {
            "topics count ${presetValue.topicData?.size}"
        }
        DatabaseFactory.dbQuery {
            val data = presetValue.topicData!!
            val userList = data.map {
                it.author
            }.distinct().map {
                User.wrapRow(findUserByAId(it).first())
            }.associateBy { it.aid!! }
            data.groupBy {
                it.community != null
            }.forEach { (t, u) ->
                if (t) {
                    addTopicsIntoCommunity(u, userList, parentDir)
                } else {
                    addTopicsIntoRoom(u, userList, parentDir)
                }
            }
        }.getOrThrow()
    }

    private suspend fun addTopicsIntoRoom(u: List<PresetTopic>, userList: Map<String, User>, parentDir: File) {
        val roomList = u.mapNotNull {
            it.room
        }.distinct().map {
            Room.wrapRow(Room.findRoomByAId(it).firstOrNull()!!)
        }.associateBy { it.aid }
        val ids = insertRoomTopicBaseLevel(u, userList, roomList)
        // 检查聊天室是属于社区的还是私有的
        val roomIsPrivate = roomList.mapValues { (_, value) ->
            checkRoomIsPrivate(value.id).getOrNull() == true
        }

        insertEncryptedTopic(roomIsPrivate, parentDir, ids, u)
        insertUnEncryptedTopic(u, roomIsPrivate, parentDir, ids, roomList, userList)
    }

    private suspend fun insertRoomTopicBaseLevel(
        u: List<PresetTopic>,
        userList: Map<String, User>,
        roomList: Map<String, Room>
    ): LongArray {
        val ids = LongArray(u.size) {
            DEFAULT_PRIMARY_KEY
        }
        val topLevelTopic = u.mapIndexed { index, addTopic ->
            val id = SnowflakeFactory.nextId()
            val level = addTopic.level
            val parent = addTopic.parent
            if (parent == null || parent == 0 || level == null || level == 0) {
                addTopic to index
                Tuple4(addTopic, index, 0, id)
            } else {
                Tuple4(addTopic, index, level, id)
            }
        }.groupBy {
            it.data3
        }
        // 从最顶层开始
        topLevelTopic.keys.sorted().forEach { level ->
            val list = topLevelTopic[level].orEmpty()
            val subIds = Topics.batchInsert(list) { (first, index, _, id) ->
                this[Topics.author] = userList[first.author]!!.id
                this[Topics.createdTime] = now()
                this[Topics.rootId] = roomList[first.room]!!.id
                this[Topics.rootType] = ObjectType.ROOM
                this[Topics.parentId] =
                    if (level == 0) roomList[first.room]!!.id else ids[index - first.parent!!]
                this[Topics.parentType] = if (level == 0) ObjectType.ROOM else ObjectType.TOPIC
                this[Topics.id] = id
            }.map {
                it[Topics.id]
            }
            subIds.forEachIndexed { index, l ->
                ids[list[index].data2] = l
            }
        }
        return ids
    }

    private suspend fun insertUnEncryptedTopic(
        presetTopicList: List<PresetTopic>,
        roomIsPrivate: Map<String, Boolean>,
        parentDir: File,
        ids: LongArray,
        roomList: Map<String, Room>,
        userList1: Map<String, User>
    ) {
        val topicsPublic = presetTopicList.mapIndexedNotNull { i, addTopic ->
            if (roomIsPrivate[addTopic.room] != true) {
                addTopic to i
            } else {
                null
            }
        }
        backend.topicSearchService.saveDocument(
            topicsPublic.map { (first, second) ->
                val content = getTopicContent(first, parentDir)
                val level = first.level
                TopicDocument(
                    ids[second],
                    content,
                    roomList[first.room]!!.id,
                    ObjectType.ROOM.name,
                    when (level) {
                        null, 0 -> roomList[first.room]!!.id
                        else -> ids[second - first.parent!!]
                    },
                    (if (level == 0) ObjectType.ROOM else ObjectType.TOPIC).name,
                    userList1[first.author]!!.id
                )
            }
        ).getOrThrow()
    }

    private suspend fun insertEncryptedTopic(
        roomIsPrivate: Map<String, Boolean>,
        parentDir: File,
        ids: LongArray,
        u: List<PresetTopic>
    ) {
        val topicsPrivate = u.mapIndexedNotNull { i, addTopic ->
            if (roomIsPrivate[addTopic.room] == true) {
                addTopic to i
            } else {
                null
            }
        }
        val rooms = u.mapNotNull {
            it.room
        }.distinct().map { roomAid ->
            val members = MemberJoins
                .join(Rooms, JoinType.INNER, MemberJoins.objectId, Rooms.id)
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .join(Users, JoinType.INNER, MemberJoins.uid, Users.id)
                .select(Users.fields)
                .where {
                    Aids.value eq roomAid
                }.map {
                    it[Users.publicKey] to it[Users.id]
                }
            roomAid to members
        }.associateBy { it.first }
        val encrypted = topicsPrivate.map { (addTopic, index) ->
            val (first, aesBytes) = encrypt(getTopicContent(addTopic, parentDir))
            Tuple4(index, first, aesBytes, addTopic)
        }
        val encryptedKeys = encrypted.flatMap { (index, _, aesBytes, topic) ->
            rooms[topic.room]!!.second.map {
                val pubKey = it.first
                val data4 = encryptAesKey(pubKey, aesBytes)
                Triple(index, data4, it.second)
            }
        }
        EncryptedTopics.batchInsert(encrypted) { (index, second) ->
            this[EncryptedTopics.topicId] = ids[index]
            this[EncryptedTopics.content] = ExposedBlob(second)
        }
        EncryptedTopicKeys.batchInsert(encryptedKeys) { (index, t4, id) ->
            this[EncryptedTopicKeys.topicId] = ids[index]
            this[EncryptedTopicKeys.encryptedAes] = ExposedBlob(t4)
            this[EncryptedTopicKeys.uid] = id
        }
    }

    private suspend fun addTopicsIntoCommunity(u: List<PresetTopic>, userList: Map<String, User>, parentDir: File) {
        val communityList = u.mapNotNull {
            it.community
        }.distinct().map {
            val rowCommunity = findCommunityByAId(it).firstOrNull()
            if (rowCommunity == null) {
                error("$it not found")
            } else {
                Community.wrapRow(rowCommunity).let { community ->
                    community.aid to community.id
                }
            }
        }.associate {
            it.first to it.second
        }
        val ids = insertCommunityTopicTopLevel(u, userList, communityList)
        backend.topicSearchService.saveDocument(
            u.mapIndexedNotNull { index, topic ->
                if (topic.community != null) {
                    val content = getTopicContent(topic, parentDir)
                    val level = topic.level

                    TopicDocument(
                        ids[index],
                        content,
                        communityList[topic.community]!!,
                        ObjectType.COMMUNITY.name,
                        when (level) {
                            null, 0 -> communityList[topic.community]!!
                            else -> ids[index - topic.parent!!]
                        },
                        when (level) {
                            0, null -> ObjectType.COMMUNITY
                            else -> ObjectType.TOPIC
                        }.name,
                        userList[topic.author]!!.id
                    )
                } else {
                    null
                }
            }
        ).getOrThrow()
    }

    private suspend fun insertCommunityTopicTopLevel(
        u: List<PresetTopic>,
        userList: Map<String, User>,
        communityList: Map<String, PrimaryKey>
    ): LongArray {
        val ids = LongArray(u.size) {
            DEFAULT_PRIMARY_KEY
        }
        // 保存top 之前的层级关系
        val topLevelTopic = u.mapIndexed { index, addTopic ->
            val id = SnowflakeFactory.nextId()
            val level = addTopic.level
            val parent = addTopic.parent
            if (parent == null || parent == 0 || level == null || level == 0) {
                Tuple4(addTopic, index, 0, id)
            } else {
                Tuple4(addTopic, index, level, id)
            }
        }.groupBy {
            it.data3
        }
        // 根据层级， 从最顶层开始
        topLevelTopic.keys.sorted().forEach { level ->
            // 添加对应层级的topic
            val list = topLevelTopic[level].orEmpty()
            val subIds = Topics.batchInsert(list) { (first, index, _, id) ->
                this[Topics.id] = id
                this[Topics.author] = userList[first.author]!!.id
                this[Topics.createdTime] = now()
                this[Topics.rootId] = communityList[first.community]!!
                this[Topics.rootType] = ObjectType.COMMUNITY
                this[Topics.parentId] =
                    if (level == 0) communityList[first.community]!! else ids[index - first.parent!!]
                this[Topics.parentType] = if (level == 0) ObjectType.COMMUNITY else ObjectType.TOPIC
            }.map {
                it[Topics.id]
            }
            // 添加完成之后，保存对应的topicId，索引是topic 在初始索引的位置
            subIds.forEachIndexed { index, topicId ->
                ids[list[index].data2] = topicId
            }
        }
        return ids
    }

    private fun getTopicContent(presetTopic: PresetTopic, parentDir: File): String {
        val content = if (presetTopic.type == "file") {
            File(parentDir, presetTopic.content).readText().replace("\r\n", "\n")
        } else {
            presetTopic.content
        }
        return content
    }

    private suspend fun addUsers(presetValue: PresetValue, parentDir: File?) {
        val userList = presetValue.userData ?: return
        Napier.i {
            "users count ${presetValue.userData?.size}"
        }
        val data = userList.map {
            val id = SnowflakeFactory.nextId()
            val derPublicKey =
                getDerPublicKeyFromPrivateKey(File(parentDir, it.privateKey).readText().replace("\r\n", "\n"))
            val ad = calcAddress(derPublicKey)
            val icon = it.icon
            if (icon == null) {
                Tuple5(it, null, derPublicKey, ad, id)
            } else {
                val path = File(parentDir, icon)
                val p = "$id/avatar.${path.extension}"
                backend.mediaService.upload(AMEDIA_BUCKET, listOf(UploadPack(p, path)))
                Tuple5(it, p, derPublicKey, ad, id)
            }
        }
        DatabaseFactory.dbQuery {
            Users.batchInsert(data) { (_, userIcon, pubKey, address, id) ->
                this[Users.id] = id
                this[Users.icon] = userIcon
                this[Users.nickname] = backend.nameService.parse(id)
                this[Users.publicKey] = pubKey
                this[Users.address] = address
                this[Users.createdTime] = now()
            }
            Aids.batchInsert(data) { (pair, _, _, _, id) ->
                this[Aids.value] = pair.id
                this[Aids.objectId] = id
                this[Aids.objectType] = ObjectType.USER
            }
        }.getOrThrow()
    }

    private suspend fun addCommunity(presetValue: PresetValue, parentDir: File?) {
        val communityData = presetValue.communityData!!
        Napier.i {
            "communities count ${presetValue.communityData?.size}"
        }
        val data = communityData.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            if (icon == null) {
                Triple(it, null, id)
            } else {
                val path = File(parentDir, icon)
                val p = "$id/community-icon.${path.extension}"
                backend.mediaService.upload(AMEDIA_BUCKET, listOf(UploadPack(p, path)))
                Triple(it, p, id)
            }
        }
        addCommunity(data)
    }

    private suspend fun addCommunity(data: List<Triple<PresetCommunity, String?, PrimaryKey>>) {
        DatabaseFactory.dbQuery {
            val systemId = findUserByAId("System").first()[Users.id]

            Communities.batchInsert(data) { (it, communityIcon, id) ->
                this[Communities.id] = id
                this[Communities.createdTime] = now()
                this[Communities.name] = it.name
                this[Communities.icon] = communityIcon
                this[Communities.owner] = systemId
            }
            Aids.batchInsert(data) { (it, _, id) ->
                this[Aids.value] = it.id
                this[Aids.objectId] = id
                this[Aids.objectType] = ObjectType.COMMUNITY
            }
            data.forEach { (c, _, id) ->
                val users = c.users.orEmpty()
                userJoinCommunity(users, id)
            }

            data.forEach { (t, _, id) ->
                val defaultRoomList = listOf(
                    "${t.id}_general" to "General",
                    "${t.id}_lobby" to "Lobby",
                    "${t.id}_support" to "Support"
                ).map { pair ->
                    Tuple4(pair.first, pair.second, SnowflakeFactory.nextId(), id)
                }
                Rooms.batchInsert(defaultRoomList) { (_, name, roomId, communityId) ->
                    this[Rooms.id] = roomId
                    this[Rooms.name] = name
                    this[Rooms.communityId] = communityId
                    this[Rooms.creator] = systemId
                    this[Rooms.createdTime] = now()
                }
                Aids.batchInsert(defaultRoomList) { (roomAid, _, roomId, _) ->
                    this[Aids.value] = roomAid.take(ROOM_ID_LENGTH)
                    this[Aids.objectId] = roomId
                    this[Aids.objectType] = ObjectType.ROOM
                }
                MemberJoins.batchInsert(defaultRoomList) { (_, _, rId, _) ->
                    this[MemberJoins.uid] = systemId
                    this[MemberJoins.objectId] = rId
                    this[MemberJoins.joinTime] = now()
                    this[MemberJoins.objectType] = ObjectType.COMMUNITY
                }
            }
        }.getOrThrow()
    }

    private suspend fun userJoinCommunity(users: List<String>, communityId: PrimaryKey) {
        users.forEach {
            val userId = findUserByAId(it).first()[Users.id]
            DatabaseFactory.addCommunityJoin(userId, communityId, now()).getOrThrow()
        }
    }
}
