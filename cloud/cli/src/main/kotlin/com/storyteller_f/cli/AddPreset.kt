package com.storyteller_f.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.DatabaseFactory
import com.storyteller_f.backend.service.ObjectListFetch.AidListFetch
import com.storyteller_f.backend.service.index.TopicDocument
import com.storyteller_f.backend.service.media.UploadPack
import com.storyteller_f.backend.service.media.uploadFilesAfterDetectContentTypeAndDimension
import com.storyteller_f.backend.service.query.createCommunityRoomsRaw
import com.storyteller_f.backend.service.query.getCommunityRawResults
import com.storyteller_f.backend.service.query.getRoomList
import com.storyteller_f.backend.service.query.getUserRawResultList
import com.storyteller_f.backend.service.query.insertMediaRefs
import com.storyteller_f.backend.service.tables.Aids
import com.storyteller_f.backend.service.tables.Communities
import com.storyteller_f.backend.service.tables.Community
import com.storyteller_f.backend.service.tables.EncryptedKeys
import com.storyteller_f.backend.service.tables.EncryptedTopics
import com.storyteller_f.backend.service.tables.MemberJoins
import com.storyteller_f.backend.service.tables.MemberJoins.joinedTime
import com.storyteller_f.backend.service.tables.MemberJoins.objectId
import com.storyteller_f.backend.service.tables.MemberJoins.objectType
import com.storyteller_f.backend.service.tables.MemberJoins.uid
import com.storyteller_f.backend.service.tables.Room
import com.storyteller_f.backend.service.tables.Rooms
import com.storyteller_f.backend.service.tables.Topics
import com.storyteller_f.backend.service.tables.User
import com.storyteller_f.backend.service.tables.Users
import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.AlgoType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PassType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.apache.tika.Tika
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.io.File
import kotlin.system.exitProcess

data class EncryptedTopicTuple(
    val encryptedKey: ByteArray,
    val aesKey: ByteArray,
    val presetTopic: InsertTopicTuple
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedTopicTuple

        if (!encryptedKey.contentEquals(other.encryptedKey)) return false
        if (!aesKey.contentEquals(other.aesKey)) return false
        if (presetTopic != other.presetTopic) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encryptedKey.contentHashCode()
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

@Suppress("LargeClass")
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
        loadCryptoLibIfNeed()
        val connected = backend
        DatabaseFactory.connect(connected.config.databaseConnection)
        DatabaseFactory.init(connected)
        Napier.i {
            "database init done."
        }
        val jsonFile = File(jsonFilePath)

        val presetValue = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<PresetValue>(jsonFile.readText())

        val parentDir = jsonFile.parentFile.canonicalFile
        val tika = Tika()
        runBlocking {
            try {
                when (val type = presetValue.type) {
                    "community" -> connected.addCommunities(presetValue, parentDir, tika)
                    "user" -> connected.addUsers(presetValue, parentDir, tika)
                    "topic" -> connected.addTopics(presetValue, parentDir, tika)
                    "room" -> connected.addRooms(presetValue, parentDir, tika)
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

    private suspend fun Backend.addRooms(presetValue: PresetValue, parentDir: File?, tika: Tika) {
        val l = presetValue.roomData ?: return
        Napier.i {
            "rooms count ${presetValue.roomData?.size}"
        }
        val (roomList, membersList) = getRoomsData(l, parentDir, tika)
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
                    this[objectType] = ObjectType.ROOM
                }
            }
        }.getOrThrow()
    }

    private suspend fun Backend.addTopics(presetValue: PresetValue, parentDir: File, tika: Tika) {
        Napier.i {
            "topics count ${presetValue.topicData?.size}"
        }
        val data = presetValue.topicData!!
        val userMap = databaseSession.getUserRawResultList(AidListFetch(data.map {
            it.author
        }.distinct())).getOrThrow().associate {
            it.user.aid!! to it.user
        }
        val roomMap = databaseSession.getRoomList(AidListFetch(data.mapNotNull {
            it.room
        })).getOrThrow().associateBy { it.aid }
        databaseSession.dbQuery {
            data.groupBy {
                when {
                    it.community != null -> ObjectType.COMMUNITY
                    it.room != null -> ObjectType.ROOM
                    else -> ObjectType.USER
                }
            }.forEach { (objectType, list) ->
                if (objectType == ObjectType.ROOM) {
                    addTopicsIntoRoom(list, userMap, parentDir, tika, roomMap)
                } else {
                    addTopics(
                        list,
                        userMap,
                        objectType,
                        getRootIdFunc(objectType, list, userMap),
                        parentDir,
                        tika
                    )
                }
            }
        }.getOrThrow()
    }

    private suspend fun Backend.addUsers(presetValue: PresetValue, parentDir: File?, tika: Tika) {
        val userList = presetValue.userData ?: return
        Napier.i {
            "users count ${presetValue.userData?.size}"
        }
        val users = getUserData(userList, parentDir, tika)
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

    private suspend fun Backend.addCommunities(presetValue: PresetValue, parentDir: File?, tika: Tika) {
        val communityData = presetValue.communityData!!
        Napier.i {
            "communities count ${presetValue.communityData?.size}"
        }
        val (memberList, l2, l3) = getCommunityData(communityData, parentDir, tika)
        databaseSession.dbQuery {
            Communities.batchInsert(l2) {
                this[Communities.id] = it.id
                this[Communities.createdTime] = it.createdTime
                this[Communities.name] = it.name
                this[Communities.icon] = it.icon
                this[Communities.owner] = it.owner
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
        }.getOrThrow()
    }

    private suspend fun Backend.addTopics(
        list: List<PresetTopic>,
        userMap: Map<String, User>,
        objectType: ObjectType,
        rootId: (PresetTopic) -> PrimaryKey,
        parentDir: File,
        tika: Tika,
    ) {
        val tuples = insertTopicsIntoCommunityOrUser(list, userMap, objectType, rootId)
        tuples.forEach { topicTuple ->
            uploadMedias(parentDir, userMap, tika, topicTuple.id, topicTuple.topic)
        }
        topicSearchService.saveDocument(
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

    private suspend fun Backend.getCommunityData(
        communityData: List<PresetCommunity>,
        parentDir: File?,
        tika: Tika
    ): Triple<List<Pair<PrimaryKey, List<PrimaryKey>>>, List<Community>, List<Triple<PrimaryKey, PrimaryKey, String>>> {
        val data = communityData.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            if (icon == null) {
                Triple(it, null, id)
            } else {
                val path = File(parentDir, icon)
                val p = "$id/community-icon.${path.extension}"
                uploadFilesAfterDetectContentTypeAndDimension(
                    tika,
                    listOf(
                        UploadPack(
                            path,
                            "community-icon.${path.extension}",
                            id,
                            path.length(),
                        )
                    )
                ).getOrThrow()
                Triple(it, p, id)
            }
        }
        val userMap = databaseSession.getUserRawResultList(AidListFetch(data.flatMap {
            it.first.users.orEmpty() + (it.first.admin ?: "System")
        }.distinct())).getOrThrow().associate {
            it.user.aid to it.user
        }
        val l1 = data.map {
            it.third to it.first.users?.map { s ->
                userMap[s]!!.id
            }.orEmpty() + userMap["System"]!!.id
        }
        val l2 = data.map {
            Community(
                it.third,
                now(),
                it.first.id,
                it.first.name,
                userMap[it.first.admin ?: "System"]!!.id,
                it.second
            )
        }
        val l3 = data.map {
            Triple(userMap[it.first.admin ?: "System"]!!.id, it.third, it.first.id)
        }
        return Triple(l1, l2, l3)
    }

    private suspend fun Backend.getUserData(
        userList: List<PresetUser>,
        parentDir: File?,
        tika: Tika
    ): List<User> {
        return userList.map {
            val id = SnowflakeFactory.nextId()
            val derPublicKey =
                getDerPublicKeyFromPrivateKey(
                    File(parentDir, it.privateKey).readText().replace("\r\n", "\n")
                ).getOrThrow()
            val ad = calcAddress(derPublicKey).getOrThrow()
            val icon = it.icon
            if (icon == null) {
                UserPresetTuple(it, null, derPublicKey, ad, id)
            } else {
                val path = File(parentDir, icon)
                val p = "$id/avatar.${path.extension}"
                uploadFilesAfterDetectContentTypeAndDimension(
                    tika,
                    listOf(
                        UploadPack(
                            path,
                            "avatar.${path.extension}",
                            id,
                            path.length()
                        )
                    )
                ).getOrThrow()
                UserPresetTuple(it, p, derPublicKey, ad, id)
            }
        }.map {
            User(
                it.presetUser.id,
                it.publicKey,
                it.address,
                it.pic,
                it.presetUser.name.takeIf { s -> s.isNotBlank() } ?: nameService.parse(it.id),
                it.id,
                now(),
                0,
                PassType.RAW,
                AlgoType.P256
            )
        }
    }

    private suspend fun Backend.getRoomsData(
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
                uploadFilesAfterDetectContentTypeAndDimension(
                    tika,
                    listOf(
                        UploadPack(
                            path,
                            "room-icon.${path.extension}",
                            id,
                            path.length(),
                        )
                    )
                ).getOrThrow()
                Triple(it, p, id)
            }
        }

        val userMap = databaseSession.getUserRawResultList(AidListFetch(l.flatMap {
            it.users + it.admin
        }.distinct())).getOrThrow().associate {
            it.user.aid to it.user
        }

        val communityMap = databaseSession.getCommunityRawResults(AidListFetch(l.mapNotNull {
            it.community
        }.distinct())).getOrThrow().associate {
            it.community.aid to it.community
        }
        return data.map { (presetRoom, pic, id) ->
            Room(
                id,
                now(),
                presetRoom.id,
                presetRoom.name,
                userMap[presetRoom.admin]!!.id,
                pic,
                communityMap[presetRoom.community]?.id
            )
        } to data.map {
            it.first.users.map { s ->
                userMap[s]!!.id
            } to it.third
        }
    }

    private suspend fun Backend.getRootIdFunc(
        objectType: ObjectType,
        list: List<PresetTopic>,
        userMap: Map<String, User>
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

    private suspend fun Backend.getCommunityMap(list: List<PresetTopic>): Map<String, PrimaryKey> {
        return databaseSession.getCommunityRawResults(AidListFetch(list.mapNotNull {
            it.community
        })).getOrThrow().associate {
            it.community.aid to it.community.id
        }
    }

    private suspend fun Backend.addTopicsIntoRoom(
        u: List<PresetTopic>,
        userList: Map<String, User>,
        parentDir: File,
        tika: Tika,
        roomMap: Map<String, Room>
    ) {
        val tuples = insertRoomTopic(u, userList, roomMap)
        // 检查聊天室是属于社区的还是私有的

        insertEncryptedTopicToRoom(parentDir, tika, roomMap, tuples.mapIndexedNotNull { i, addTopic ->
            if (roomMap[addTopic.topic.room]?.communityId == null) {
                addTopic
            } else {
                null
            }
        })
        insertUnEncryptedTopicToRoom(
            parentDir,
            tuples,
            roomMap,
            userList,
            tika,
            tuples.mapIndexedNotNull { i, addTopic ->
                if (roomMap[addTopic.topic.room]?.communityId != null) {
                    addTopic.topic to i
                } else {
                    null
                }
            }
        )
    }

    private suspend fun insertRoomTopic(
        u: List<PresetTopic>,
        userList: Map<String, User>,
        roomMap: Map<String, Room>
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
            this[Topics.rootId] = roomMap[first.room]!!.id
            this[Topics.rootType] = ObjectType.ROOM
            this[Topics.parentId] =
                if (level == 0) roomMap[first.room]!!.id else topLevelTopic[index - first.parent!!].id
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

    private suspend fun Backend.insertUnEncryptedTopicToRoom(
        parentDir: File,
        tuples: List<InsertTopicTuple>,
        roomMap: Map<String, Room>,
        userMap: Map<String, User>,
        tika: Tika,
        topicsPublic: List<Pair<PresetTopic, Int>>
    ) {
        topicSearchService.saveDocument(
            topicsPublic.map { (first, second) ->
                val content = getTopicContent(first, parentDir)
                val level = first.level
                TopicDocument(
                    tuples[second].id,
                    content,
                    roomMap[first.room]!!.id,
                    ObjectType.ROOM.name,
                    when (level) {
                        null, 0 -> roomMap[first.room]!!.id
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

    private suspend fun Backend.uploadMedias(
        parentDir: File,
        userMap: Map<String, User>,
        tika: Tika,
        topicId: PrimaryKey,
        presetTopic: PresetTopic
    ) {
        val content = getTopicContent(presetTopic, parentDir)
        val mediaLink = extractMarkdownMediaLink(content)
        val mediaNames = mediaLink.map {
            userMap[presetTopic.author]!!.id to it
        }
        uploadFilesAfterDetectContentTypeAndDimension(tika, mediaNames.map { (author, pic) ->
            val path = File(parentDir, "medias/topics/$pic")
            UploadPack(path, pic, author, path.length())
        }).getOrThrow()
        databaseSession.insertMediaRefs(topicId, ObjectType.TOPIC, mediaNames)
    }

    private suspend fun Backend.insertEncryptedTopicToRoom(
        parentDir: File,
        tika: Tika,
        roomMap: Map<String, Room>,
        topicsPrivate: List<InsertTopicTuple>
    ) {
        val roomMembers = topicsPrivate.mapNotNull {
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
        val encrypted = topicsPrivate.map {
            val (first, aesBytes) = encryptData(this@AddPreset.getTopicContent(it.topic, parentDir)).getOrThrow()
            EncryptedTopicTuple(first, aesBytes, it)
        }
        val encryptedKeys = encrypted.flatMap { (_, aesBytes, topic) ->
            roomMembers[topic.topic.room]!!.map { (first, second) ->
                Triple(topic.id, eciesEncrypt(first, aesBytes).getOrThrow(), second)
            }
        }
        EncryptedTopics.batchInsert(encrypted) { (bytes, _, t) ->
            this[EncryptedTopics.topicId] = t.id
            this[EncryptedTopics.content] = ExposedBlob(bytes)
        }
        EncryptedKeys.batchInsert(encryptedKeys) { (index, t4, id) ->
            this[EncryptedKeys.topicId] = index
            this[EncryptedKeys.encryptedAes] = ExposedBlob(t4)
            this[EncryptedKeys.uid] = id
        }
        topicsPrivate.forEach { topic ->
            val room = roomMap[topic.topic.room]
            if (room != null) {
                val content = this@AddPreset.getTopicContent(topic.topic, parentDir)
                uploadFilesAfterDetectContentTypeAndDimension(tika, extractMarkdownMediaLink(content).map {
                    val path = File(parentDir, "medias/topics/$it")
                    UploadPack(path, it, room.id, path.length())
                }).getOrThrow()
            }
        }
    }

    private suspend fun insertTopicsIntoCommunityOrUser(
        u: List<PresetTopic>,
        userList: Map<String, User>,
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
