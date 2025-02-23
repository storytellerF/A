package com.storyteller_f.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.crypto_jvm.addProviderForJvm
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.media.uploadFiles
import com.storyteller_f.shared.*
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.PresetCommunity
import com.storyteller_f.shared.obj.PresetTopic
import com.storyteller_f.shared.obj.PresetUser
import com.storyteller_f.shared.obj.PresetValue
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.apache.tika.Tika
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
        val tika = Tika()
        runBlocking {
            try {
                when (val type = presetValue.type) {
                    "community" -> addCommunity(presetValue, parentDir, tika)
                    "user" -> addUsers(presetValue, parentDir, tika)
                    "topic" -> addTopics(presetValue, parentDir, tika)
                    "room" -> addRooms(presetValue, parentDir, tika)
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

    private suspend fun addRooms(presetValue: PresetValue, parentDir: File?, tika: Tika) {
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
                uploadFiles(
                    tika,
                    backend,
                    listOf(Triple(path, "$id/${"room-icon.${path.extension}"}", null))
                ).getOrThrow()
                Triple(it, p, id)
            }
        }
        DatabaseFactory.dbQuery {
            val userMap = l.flatMap {
                it.users + it.admin
            }.distinct().map {
                User.wrapRow(findUserByAid(it).first())
            }.associateBy { it.aid }
            val communityMap = l.mapNotNull {
                it.community
            }.distinct().map {
                Community.wrapRow(findCommunityByAid(it).first())
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

    private suspend fun addTopics(presetValue: PresetValue, parentDir: File, tika: Tika) {
        Napier.i {
            "topics count ${presetValue.topicData?.size}"
        }
        DatabaseFactory.dbQuery {
            val data = presetValue.topicData!!
            val userList = DatabaseFactory.getUsersByAids(data.map {
                it.author
            }.distinct()).getOrThrow().associate {
                it.first.aid!! to it.first
            }
            data.groupBy {
                it.community != null
            }.forEach { (t, u) ->
                if (t) {
                    addTopicsIntoCommunity(u, userList, parentDir, tika)
                } else {
                    addTopicsIntoRoom(u, userList, parentDir, tika)
                }
            }
        }.getOrThrow()
    }

    private suspend fun addTopicsIntoRoom(
        u: List<PresetTopic>,
        userList: Map<String, UserInfo>,
        parentDir: File,
        tika: Tika
    ) {
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

        insertEncryptedTopicToRoom(roomIsPrivate, parentDir, ids, u, tika, roomList)
        insertUnEncryptedTopicToRoom(u, roomIsPrivate, parentDir, ids, roomList, userList, tika)
    }

    private suspend fun insertRoomTopicBaseLevel(
        u: List<PresetTopic>,
        userList: Map<String, UserInfo>,
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
                InsertTopicTuple(addTopic, index, 0, id)
            } else {
                InsertTopicTuple(addTopic, index, level, id)
            }
        }.groupBy {
            it.level
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
            Aids.batchInsert(list.filter {
                !it.topic.aid.isNullOrBlank()
            }) { (first, _, _, id) ->
                this[Aids.value] = first.aid!!
                this[Aids.objectId] = id
                this[Aids.objectType] = ObjectType.TOPIC
            }
            subIds.forEachIndexed { index, l ->
                ids[list[index].originalIndex] = l
            }
        }
        return ids
    }

    private suspend fun insertUnEncryptedTopicToRoom(
        presetTopicList: List<PresetTopic>,
        roomIsPrivate: Map<String, Boolean>,
        parentDir: File,
        ids: LongArray,
        roomList: Map<String, Room>,
        userList: Map<String, UserInfo>,
        tika: Tika
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
                    userList[first.author]!!.id
                )
            }
        ).getOrThrow()
        presetTopicList.forEachIndexed { _, topic ->
            if (topic.room != null) {
                val content = getTopicContent(topic, parentDir)
                uploadFiles(tika, backend, extractMarkdownMediaLink(content).map {
                    Triple(File(parentDir, "images/topics/$it"), "${userList[topic.author]!!.id}/$it", null)
                }).getOrThrow()
            }
        }
    }

    data class EncryptedTopicTuple(val index: Int, val encryptedKey: ByteArray, val aesKey: ByteArray, val presetTopic: PresetTopic)

    private suspend fun insertEncryptedTopicToRoom(
        roomIsPrivate: Map<String, Boolean>,
        parentDir: File,
        ids: LongArray,
        u: List<PresetTopic>,
        tika: Tika,
        roomList: Map<String, Room>
    ) {
        val topicsPrivate = u.mapIndexedNotNull { i, addTopic ->
            if (roomIsPrivate[addTopic.room] == true) {
                addTopic to i
            } else {
                null
            }
        }
        val roomMembers = u.mapNotNull {
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
        }.associate { it }
        val encrypted = topicsPrivate.map { (addTopic, index) ->
            val (first, aesBytes) = encrypt(getTopicContent(addTopic, parentDir))
            EncryptedTopicTuple(index, first, aesBytes, addTopic)
        }
        val encryptedKeys = encrypted.flatMap { (index, _, aesBytes, topic) ->
            roomMembers[topic.room]!!.map {
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
        u.forEachIndexed { index, topic ->
            if (topic.room != null) {
                val room = roomList[topic.room]
                if (room != null) {
                    val content = getTopicContent(topic, parentDir)
                    uploadFiles(tika, backend, extractMarkdownMediaLink(content).map {
                        Triple(File(parentDir, "images/topics/$it"), "${room.id}/$it", null)
                    }).getOrThrow()
                }
            } else {
                null
            }
        }
    }

    private suspend fun addTopicsIntoCommunity(
        u: List<PresetTopic>,
        userList: Map<String, UserInfo>,
        parentDir: File,
        tika: Tika
    ) {
        val communityList = u.mapNotNull {
            it.community
        }.distinct().map {
            val rowCommunity = findCommunityByAid(it).firstOrNull()
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
        val ids = insertCommunityTopics(u, userList, communityList)
        u.forEachIndexed { index, topic ->
            if (topic.community != null) {
                val content = getTopicContent(topic, parentDir)
                val mediaLink = extractMarkdownMediaLink(content)
                uploadFiles(tika, backend, mediaLink.map {
                    Triple(File(parentDir, "images/topics/$it"), "${userList[topic.author]!!.id}/$it", null)
                }).getOrThrow()
            } else {
                null
            }
        }
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

    data class InsertTopicTuple(val topic: PresetTopic, val originalIndex: Int, val level: Int, val id: PrimaryKey)

    private suspend fun insertCommunityTopics(
        u: List<PresetTopic>,
        userList: Map<String, UserInfo>,
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
                InsertTopicTuple(addTopic, index, 0, id)
            } else {
                InsertTopicTuple(addTopic, index, level, id)
            }
        }.groupBy {
            it.level
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
            Aids.batchInsert(list.filter {
                !it.topic.aid.isNullOrBlank()
            }) { (first, _, _, id) ->
                this[Aids.value] = first.aid!!
                this[Aids.objectId] = id
                this[Aids.objectType] = ObjectType.TOPIC
            }
            // 添加完成之后，保存对应的topicId，索引是topic 在初始索引的位置
            subIds.forEachIndexed { index, topicId ->
                ids[list[index].originalIndex] = topicId
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

    data class UserPresetTuple(val presetUser: PresetUser, val pic: String?, val publicKey: String, val address: String, val id: PrimaryKey)

    private suspend fun addUsers(presetValue: PresetValue, parentDir: File?, tika: Tika) {
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
                UserPresetTuple(it, null, derPublicKey, ad, id)
            } else {
                val path = File(parentDir, icon)
                val p = "$id/avatar.${path.extension}"
                uploadFiles(
                    tika,
                    backend,
                    listOf(Triple(path, "$id/${"avatar.${path.extension}"}", null))
                ).getOrThrow()
                UserPresetTuple(it, p, derPublicKey, ad, id)
            }
        }
        DatabaseFactory.dbQuery {
            Users.batchInsert(data) { (v, userIcon, pubKey, address, id) ->
                this[Users.id] = id
                this[Users.icon] = userIcon
                this[Users.nickname] = v.name.takeIf { it.isNotBlank() } ?: backend.nameService.parse(id)
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

    private suspend fun addCommunity(presetValue: PresetValue, parentDir: File?, tika: Tika) {
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
                uploadFiles(
                    tika,
                    backend,
                    listOf(Triple(path, "$id/${"community-icon.${path.extension}"}", null))
                ).getOrThrow()
                Triple(it, p, id)
            }
        }
        addCommunity(data)
    }

    private suspend fun addCommunity(data: List<Triple<PresetCommunity, String?, PrimaryKey>>) {
        DatabaseFactory.dbQuery {
            val userMap = data.flatMap {
                it.first.users.orEmpty() + (it.first.admin ?: "System")
            }.distinct().map {
                User.wrapRow(findUserByAid(it).first())
            }.associateBy { it.aid }
            Communities.batchInsert(data) { (it, communityIcon, id) ->
                this[Communities.id] = id
                this[Communities.createdTime] = now()
                this[Communities.name] = it.name
                this[Communities.icon] = communityIcon
                this[Communities.owner] = userMap[it.admin ?: "System"]!!.id
            }
            Aids.batchInsert(data) { (it, _, id) ->
                this[Aids.value] = it.id
                this[Aids.objectId] = id
                this[Aids.objectType] = ObjectType.COMMUNITY
            }
            data.forEach { (c, _, id) ->
                val users = (c.users.orEmpty() + (c.admin ?: "System")).distinct()
                userJoinCommunity(users, id)
            }

            data.forEach { (t, _, id) ->
                createCommunityRooms(id, userMap[t.admin ?: "System"]!!.id, t.id)
            }
        }.getOrThrow()
    }

    private suspend fun userJoinCommunity(users: List<String>, communityId: PrimaryKey) {
        users.forEach {
            val userId = findUserByAid(it).first()[Users.id]
            DatabaseFactory.addCommunityJoin(userId, communityId, now()).getOrThrow()
        }
    }
}
