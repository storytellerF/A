package com.storyteller_f.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.crypto_jvm.addProviderForJvm
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.media.uploadFiles
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.encrypt
import com.storyteller_f.shared.encryptAesKey
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.tables.MemberJoins.joinTime
import com.storyteller_f.tables.MemberJoins.objectId
import com.storyteller_f.tables.MemberJoins.objectType
import com.storyteller_f.tables.MemberJoins.uid
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

data class EncryptedTopicTuple(
    val index: Int,
    val encryptedKey: ByteArray,
    val aesKey: ByteArray,
    val presetTopic: PresetTopic
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedTopicTuple

        if (index != other.index) return false
        if (!encryptedKey.contentEquals(other.encryptedKey)) return false
        if (!aesKey.contentEquals(other.aesKey)) return false
        if (presetTopic != other.presetTopic) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + encryptedKey.contentHashCode()
        result = 31 * result + aesKey.contentHashCode()
        result = 31 * result + presetTopic.hashCode()
        return result
    }
}

data class UserPresetTuple(
    val presetUser: PresetUser,
    val pic: String?,
    val publicKey: String,
    val address: String,
    val id: PrimaryKey
)

data class InsertTopicTuple(val topic: PresetTopic, val originalIndex: Int, val level: Int, val id: PrimaryKey)

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
        DatabaseFactory.connect(backend.config.databaseConnection)
        DatabaseFactory.init()
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
                    "community" -> addCommunities(presetValue, parentDir, tika)
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
        val (l2, l1) = getRoomsData(l, parentDir, tika)
        DatabaseFactory.dbQuery {
            Rooms.batchInsert(l2) {
                this[Rooms.id] = it.id
                this[Rooms.icon] = it.icon
                this[Rooms.name] = it.name
                this[Rooms.communityId] = it.communityId
                this[Rooms.creator] = it.creator
                this[Rooms.createdTime] = it.createdTime
                this[Rooms.memberCount] = it.memberCount
            }
            Aids.batchInsert(l2) {
                this[Aids.value] = it.aid
                this[Aids.objectId] = it.id
                this[Aids.objectType] = ObjectType.ROOM
            }
            l1.forEachIndexed { _, addRoom ->
                MemberJoins.batchInsert(addRoom.first) {
                    this[uid] = it
                    this[objectId] = addRoom.second
                    this[joinTime] = now()
                    this[objectType] = ObjectType.ROOM
                }
            }
        }.getOrThrow()
    }

    private suspend fun addTopics(presetValue: PresetValue, parentDir: File, tika: Tika) {
        Napier.i {
            "topics count ${presetValue.topicData?.size}"
        }
        val data = presetValue.topicData!!
        DatabaseFactory.dbQuery {
            val userMap = DatabaseFactory.getUsersByAids(data.map {
                it.author
            }.distinct()).getOrThrow().associate {
                it.first.aid!! to it.first
            }
            data.groupBy {
                when {
                    it.community != null -> ObjectType.COMMUNITY
                    it.room != null -> ObjectType.ROOM
                    else -> ObjectType.USER
                }
            }.forEach { (objectType, list) ->
                if (objectType == ObjectType.ROOM) {
                    addTopicsIntoRoom(list, userMap, parentDir, tika)
                } else {
                    addTopics(list, userMap, objectType, getRootIdFunc(objectType, list, userMap), parentDir, tika)
                }
            }
        }.getOrThrow()
    }

    private suspend fun addUsers(presetValue: PresetValue, parentDir: File?, tika: Tika) {
        val userList = presetValue.userData ?: return
        Napier.i {
            "users count ${presetValue.userData?.size}"
        }
        val users = getUserData(userList, parentDir, tika)
        DatabaseFactory.dbQuery {
            Users.batchInsert(users) {
                this[Users.id] = it.id
                this[Users.icon] = it.icon
                this[Users.nickname] = it.nickname
                this[Users.publicKey] = it.publicKey
                this[Users.address] = it.address
                this[Users.createdTime] = it.createdTime
            }
            Aids.batchInsert(users) {
                this[Aids.value] = it.aid!!
                this[Aids.objectId] = it.id
                this[Aids.objectType] = ObjectType.USER
            }
        }.getOrThrow()
    }

    private suspend fun addCommunities(presetValue: PresetValue, parentDir: File?, tika: Tika) {
        val communityData = presetValue.communityData!!
        Napier.i {
            "communities count ${presetValue.communityData?.size}"
        }
        val (l1, l2, l3) = getCommunityData(communityData, parentDir, tika)
        DatabaseFactory.dbQuery {
            Communities.batchInsert(l2) {
                this[Communities.id] = it.id
                this[Communities.createdTime] = it.createdTime
                this[Communities.name] = it.name
                this[Communities.icon] = it.icon
                this[Communities.owner] = it.owner
                this[Communities.memberCount] = it.memberCount
            }
            Aids.batchInsert(l2) {
                this[Aids.value] = it.aid
                this[Aids.objectId] = it.id
                this[Aids.objectType] = ObjectType.COMMUNITY
            }
            l1.forEach { (c, communityId) ->
                MemberJoins.batchInsert(c) {
                    val userId = it
                    this[joinTime] = now()
                    this[uid] = userId
                    this[objectId] = communityId
                    this[objectType] = ObjectType.COMMUNITY
                }
            }
            l3.forEach { (c, communityId, aid) ->
                createCommunityRooms(communityId, c, aid)
            }
        }.getOrThrow()
    }

    private suspend fun addTopics(
        list: List<PresetTopic>,
        userMap: Map<String, UserInfo>,
        objectType: ObjectType,
        rootId: (PresetTopic) -> PrimaryKey,
        parentDir: File,
        tika: Tika
    ) {
        val tuples = insertTopicsIntoCommunityOrUser(list, userMap, objectType, rootId)
        tuples.forEach { topicTuple ->
            uploadMedias(parentDir, userMap, tika, topicTuple.id, topicTuple.topic)
        }
        backend.topicSearchService.saveDocument(
            tuples.mapIndexed { index, topicTuple ->
                val content = getTopicContent(topicTuple.topic, parentDir)
                val level = topicTuple.level

                TopicDocument(
                    topicTuple.id,
                    content,
                    rootId(topicTuple.topic),
                    objectType.name,
                    when (level) {
                        0 -> rootId(topicTuple.topic)
                        else -> tuples[index - topicTuple.topic.parent!!].id
                    },
                    when (level) {
                        0 -> objectType
                        else -> ObjectType.TOPIC
                    }.name,
                    userMap[topicTuple.topic.author]!!.id
                )
            }
        ).getOrThrow()
    }

    private suspend fun getCommunityData(
        communityData: List<PresetCommunity>,
        parentDir: File?,
        tika: Tika
    ): Triple<List<Pair<List<PrimaryKey>, PrimaryKey>>, List<Community>, List<Triple<PrimaryKey, PrimaryKey, String>>> {
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
        val userMap = DatabaseFactory.dbQuery {
            data.flatMap {
                it.first.users.orEmpty() + (it.first.admin ?: "System")
            }.distinct().map {
                User.wrapRow(findUserByAid(it).first())
            }.associateBy { it.aid }
        }.getOrThrow()
        val l1 = data.map {
            it.first.users?.map { s ->
                userMap[s]!!.id
            }.orEmpty() to it.third
        }
        val l2 = data.map {
            Community(
                it.third,
                now(),
                it.first.id,
                it.first.name,
                userMap[it.first.admin ?: "System"]!!.id,
                0,
                it.second
            )
        }
        val l3 = data.map {
            Triple(userMap[it.first.admin ?: "System"]!!.id, it.third, it.first.id)
        }
        return Triple(l1, l2, l3)
    }

    private suspend fun getUserData(
        userList: List<PresetUser>,
        parentDir: File?,
        tika: Tika
    ): List<User> {
        return userList.map {
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
        }.map {
            User(
                it.presetUser.id,
                it.publicKey,
                it.address,
                it.pic,
                it.presetUser.name.takeIf { s -> s.isNotBlank() } ?: backend.nameService.parse(it.id),
                it.id,
                now()
            )
        }
    }

    private suspend fun getRoomsData(
        l: List<PresetRoom>,
        parentDir: File?,
        tika: Tika
    ): Pair<List<Room>, List<Pair<List<PrimaryKey>, PrimaryKey>>> {
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
        val (userMap, communityMap) = DatabaseFactory.dbQuery {
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
            userMap to communityMap
        }.getOrThrow()
        return data.map { (first, second, third) ->
            Room(
                third,
                now(),
                first.id,
                first.name,
                userMap[first.admin]!!.id,
                first.users.size.toLong(),
                second,
                communityMap[first.community]?.id
            )
        } to data.map {
            it.first.users.map { s ->
                userMap[s]!!.id
            } to it.third
        }
    }

    private fun getRootIdFunc(
        objectType: ObjectType,
        list: List<PresetTopic>,
        userMap: Map<String, UserInfo>
    ): (PresetTopic) -> PrimaryKey {
        return if (objectType == ObjectType.USER) {
            val userIdMap = userMap.mapValues {
                it.value.id
            }
            val rootId: (PresetTopic) -> PrimaryKey = {
                userIdMap[it.author]!!
            }
            rootId
        } else {
            val communityMap = getCommunityMap(list)
            val rootId: (PresetTopic) -> PrimaryKey = {
                communityMap[it.community]!!
            }
            rootId
        }
    }

    private fun getCommunityMap(list: List<PresetTopic>): Map<String, PrimaryKey> {
        val communityMap = list.mapNotNull {
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
        return communityMap
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
        val tuples = insertRoomTopic(u, userList, roomList)
        // 检查聊天室是属于社区的还是私有的
        val roomIsPrivate = roomList.mapValues { (_, value) ->
            checkRoomIsPrivate(value.id).getOrNull() == true
        }

        insertEncryptedTopicToRoom(roomIsPrivate, parentDir, tuples, tika, roomList)
        insertUnEncryptedTopicToRoom(roomIsPrivate, parentDir, tuples, roomList, userList, tika)
    }

    private suspend fun insertRoomTopic(
        u: List<PresetTopic>,
        userList: Map<String, UserInfo>,
        roomList: Map<String, Room>
    ): List<InsertTopicTuple> {
        val topLevelTopic = u.mapIndexed { index, addTopic ->
            val id = SnowflakeFactory.nextId()
            val level = addTopic.level
            val parent = addTopic.parent
            if (parent == null || parent == 0 || level == null || level == 0) {
                InsertTopicTuple(addTopic, index, 0, id)
            } else {
                InsertTopicTuple(addTopic, index, level, id)
            }
        }
        // 从最顶层开始
        Topics.batchInsert(topLevelTopic) { (first, index, level, id) ->
            this[Topics.author] = userList[first.author]!!.id
            this[Topics.createdTime] = now()
            this[Topics.rootId] = roomList[first.room]!!.id
            this[Topics.rootType] = ObjectType.ROOM
            this[Topics.parentId] =
                if (level == 0) roomList[first.room]!!.id else topLevelTopic[index - first.parent!!].id
            this[Topics.parentType] = if (level == 0) ObjectType.ROOM else ObjectType.TOPIC
            this[Topics.id] = id
        }
        Aids.batchInsert(topLevelTopic.filter {
            !it.topic.aid.isNullOrBlank()
        }) { (first, _, _, id) ->
            this[Aids.value] = first.aid!!
            this[Aids.objectId] = id
            this[Aids.objectType] = ObjectType.TOPIC
        }
        return topLevelTopic
    }

    private suspend fun insertUnEncryptedTopicToRoom(
        roomIsPrivate: Map<String, Boolean>,
        parentDir: File,
        tuples: List<InsertTopicTuple>,
        roomList: Map<String, Room>,
        userMap: Map<String, UserInfo>,
        tika: Tika
    ) {
        val topicsPublic = tuples.mapIndexedNotNull { i, addTopic ->
            if (roomIsPrivate[addTopic.topic.room] != true) {
                addTopic.topic to i
            } else {
                null
            }
        }
        backend.topicSearchService.saveDocument(
            topicsPublic.map { (first, second) ->
                val content = getTopicContent(first, parentDir)
                val level = first.level
                TopicDocument(
                    tuples[second].id,
                    content,
                    roomList[first.room]!!.id,
                    ObjectType.ROOM.name,
                    when (level) {
                        null, 0 -> roomList[first.room]!!.id
                        else -> tuples[second - first.parent!!].id
                    },
                    (if (level == 0) ObjectType.ROOM else ObjectType.TOPIC).name,
                    userMap[first.author]!!.id
                )
            }
        ).getOrThrow()
        topicsPublic.forEachIndexed { _, topic ->
            uploadMedias(parentDir, userMap, tika, tuples[topic.second].id, topic.first)
        }
    }

    private suspend fun uploadMedias(
        parentDir: File,
        userMap: Map<String, UserInfo>,
        tika: Tika,
        topicId: PrimaryKey,
        presetTopic: PresetTopic
    ) {
        val content = getTopicContent(presetTopic, parentDir)
        val mediaLink = extractMarkdownMediaLink(content)
        val mediaNames = mediaLink.map {
            userMap[presetTopic.author]!!.id to it
        }
        uploadFiles(tika, backend, mediaNames.map { (author, p) ->
            Triple(File(parentDir, "images/topics/$p"), "${author}/$p", null)
        }).getOrThrow()
        DatabaseFactory.insertMediaRefs(topicId, ObjectType.TOPIC, mediaNames)
    }

    private suspend fun insertEncryptedTopicToRoom(
        roomIsPrivate: Map<String, Boolean>,
        parentDir: File,
        tuples: List<InsertTopicTuple>,
        tika: Tika,
        roomList: Map<String, Room>
    ) {
        val topicsPrivate = tuples.mapIndexedNotNull { i, addTopic ->
            if (roomIsPrivate[addTopic.topic.room] == true) {
                addTopic.topic to i
            } else {
                null
            }
        }
        val roomMembers = tuples.mapNotNull {
            it.topic.room
        }.distinct().map { roomAid ->
            val members = MemberJoins
                .join(Rooms, JoinType.INNER, objectId, Rooms.id)
                .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
                .join(Users, JoinType.INNER, uid, Users.id)
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
            this[EncryptedTopics.topicId] = tuples[index].id
            this[EncryptedTopics.content] = ExposedBlob(second)
        }
        EncryptedTopicKeys.batchInsert(encryptedKeys) { (index, t4, id) ->
            this[EncryptedTopicKeys.topicId] = tuples[index].id
            this[EncryptedTopicKeys.encryptedAes] = ExposedBlob(t4)
            this[EncryptedTopicKeys.uid] = id
        }
        topicsPrivate.forEachIndexed { _, topic ->
            val room = roomList[topic.first.room]
            if (room != null) {
                val content = getTopicContent(topic.first, parentDir)
                uploadFiles(tika, backend, extractMarkdownMediaLink(content).map {
                    Triple(File(parentDir, "images/topics/$it"), "${room.id}/$it", null)
                }).getOrThrow()
            }
        }
    }

    private suspend fun insertTopicsIntoCommunityOrUser(
        u: List<PresetTopic>,
        userList: Map<String, UserInfo>,
        rootType: ObjectType,
        rootId: (PresetTopic) -> PrimaryKey
    ): List<InsertTopicTuple> {
        val topicTuples = u.mapIndexed { index, addTopic ->
            val id = SnowflakeFactory.nextId()
            val level = addTopic.level
            val parent = addTopic.parent
            if (parent == null || parent == 0 || level == null || level == 0) {
                InsertTopicTuple(addTopic, index, 0, id)
            } else {
                InsertTopicTuple(addTopic, index, level, id)
            }
        }

        Topics.batchInsert(topicTuples) { (first, index, level, id) ->
            this[Topics.id] = id
            this[Topics.author] = userList[first.author]!!.id
            this[Topics.createdTime] = now()
            this[Topics.rootId] = rootId(first)
            this[Topics.rootType] = rootType
            this[Topics.parentId] =
                if (level == 0) rootId(first) else topicTuples[index - first.parent!!].id
            this[Topics.parentType] = if (level == 0) rootType else ObjectType.TOPIC
        }
        Aids.batchInsert(topicTuples.filter {
            !it.topic.aid.isNullOrBlank()
        }) { (first, _, _, id) ->
            this[Aids.value] = first.aid!!
            this[Aids.objectId] = id
            this[Aids.objectType] = ObjectType.TOPIC
        }

        return topicTuples
    }

    private fun getTopicContent(presetTopic: PresetTopic, parentDir: File): String {
        val content = if (presetTopic.type == "file") {
            File(parentDir, presetTopic.content).readText().replace("\r\n", "\n")
        } else {
            presetTopic.content
        }
        return content
    }
}
