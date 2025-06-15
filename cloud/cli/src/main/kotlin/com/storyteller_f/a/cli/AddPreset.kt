package com.storyteller_f.a.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.DatabaseFactory
import com.storyteller_f.backend.service.createCommunityRoomsRaw
import com.storyteller_f.backend.service.index.TopicDocument
import com.storyteller_f.backend.service.media.uploadFilesAfterDetectContentTypeAndDimension
import com.storyteller_f.backend.service.tables.*
import com.storyteller_f.backend.service.tables.MemberJoins.joinedTime
import com.storyteller_f.backend.service.tables.MemberJoins.objectId
import com.storyteller_f.backend.service.tables.MemberJoins.objectType
import com.storyteller_f.backend.service.tables.MemberJoins.uid
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

class EncryptedTopicTuple(
    val encryptedContent: ByteArray,
    val aesKey: ByteArray,
    val id: PrimaryKey,
    val presetTopic: PresetTopic
)

data class UserPresetTuple(
    val presetUser: PresetUser,
    val pic: PrimaryKey?,
    val publicKey: String,
    val address: String,
    val id: PrimaryKey
)

class InsertTopicTuple(
    val topic: PresetTopic,
    val originalIndex: Int,
    val level: Int,
    val id: PrimaryKey,
    val content: ByteArray,
    val isEncrypted: Boolean
)

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
        val userMap = exposedDatabase.userDatabase.getUserRawResultList(ObjectListFetch.AidListFetch(data.map {
            it.author
        }.distinct())).getOrThrow().associate {
            it.user.aid!! to it.user
        }
        val roomMap = exposedDatabase.roomData.getRoomList(ObjectListFetch.AidListFetch(data.mapNotNull {
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
                val mediaInfo = uploadFilesAfterDetectContentTypeAndDimension(
                    tika,
                    listOf(
                        UploadPack(
                            path,
                            "community-icon.${path.extension}",
                            id,
                            path.length(),
                        )
                    )
                ).getOrThrow().first()
                Triple(it, mediaInfo?.id, id)
            }
        }
        val userMap = exposedDatabase.userDatabase.getUserRawResultList(ObjectListFetch.AidListFetch(data.flatMap {
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
                val mediaInfo = uploadFilesAfterDetectContentTypeAndDimension(
                    tika,
                    listOf(
                        UploadPack(
                            path,
                            "avatar.${path.extension}",
                            id,
                            path.length()
                        )
                    )
                ).getOrThrow().first()
                UserPresetTuple(it, mediaInfo?.id, derPublicKey, ad, id)
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
                val mediaInfo = uploadFilesAfterDetectContentTypeAndDimension(
                    tika,
                    listOf(
                        UploadPack(
                            path,
                            "room-icon.${path.extension}",
                            id,
                            path.length(),
                        )
                    )
                ).getOrThrow().first()
                Triple(it, mediaInfo?.id, id)
            }
        }

        val userMap = exposedDatabase.userDatabase.getUserRawResultList(
            ObjectListFetch.AidListFetch(l.flatMap {
                it.users + it.admin
            }.distinct())
        ).getOrThrow().associate {
            it.user.aid to it.user
        }

        val communityMap =
            exposedDatabase.communityDatabase.getCommunityRawResults(ObjectListFetch.AidListFetch(l.mapNotNull {
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
        return exposedDatabase.communityDatabase.getCommunityRawResults(ObjectListFetch.AidListFetch(list.mapNotNull {
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
        val privateRoomList = u.filter {
            roomMap[it.room]?.communityId == null
        }
        val publicRoomList = u.filter {
            roomMap[it.room]?.communityId != null
        }
        insertEncryptedTopicToRoom(parentDir, tika, roomMap, privateRoomList, userList)
        insertUnEncryptedTopicToRoom(
            parentDir,
            roomMap,
            userList,
            tika,
            publicRoomList
        )
    }

    private suspend fun insertRoomTopic(
        u: List<Triple<PresetTopic, ByteArray, Boolean>>,
        userList: Map<String, User>,
        roomMap: Map<String, Room>
    ): List<InsertTopicTuple> {
        val topLevelTopic = u.mapIndexed { index, addTopic ->
            val id = SnowflakeFactory.nextId()
            val level = addTopic.first.level
            val parent = addTopic.first.parent
            if (parent == null || parent == 0 || level == null || level == 0) {
                InsertTopicTuple(addTopic.first, index, 0, id, addTopic.second, addTopic.third)
            } else {
                InsertTopicTuple(addTopic.first, index, level, id, addTopic.second, addTopic.third)
            }
        }
        // 从最顶层开始
        Topics.batchInsert(topLevelTopic) {
            val presetTopic = it.topic
            val level = it.level
            val id = it.id
            val index = it.originalIndex
            this[Topics.author] = userList[presetTopic.author]!!.id
            this[Topics.createdTime] = now()
            this[Topics.rootId] = roomMap[presetTopic.room]!!.id
            this[Topics.rootType] = ObjectType.ROOM
            this[Topics.parentId] =
                if (level == 0) roomMap[presetTopic.room]!!.id else topLevelTopic[index - presetTopic.parent!!].id
            this[Topics.parentType] = if (level == 0) ObjectType.ROOM else ObjectType.TOPIC
            this[Topics.id] = id
            this[Topics.content] = ExposedBlob(it.content)
            this[Topics.isEncrypted] = it.isEncrypted
        }
        Aids.batchInsert(topLevelTopic.filter {
            !it.topic.aid.isNullOrBlank()
        }) {
            val first = it.topic
            val id = it.id
            this[Aids.value] = first.aid!!
            this[Aids.objectId] = id
            this[Aids.objectType] = ObjectType.TOPIC
        }
        return topLevelTopic
    }

    private suspend fun Backend.insertUnEncryptedTopicToRoom(
        parentDir: File,
        roomMap: Map<String, Room>,
        userMap: Map<String, User>,
        tika: Tika,
        publicRoomList: List<PresetTopic>
    ) {
        val tuples = insertRoomTopic(publicRoomList.map {
            Triple(it, it.content.encodeToByteArray(), false)
        }, userMap, roomMap)
        val topicsPublic = tuples.mapIndexedNotNull { i, addTopic ->
            if (roomMap[addTopic.topic.room]?.communityId != null) {
                addTopic.topic to i
            } else {
                null
            }
        }
        topicSearchService.saveDocument(
            topicsPublic.map { (first, index) ->
                val content = getTopicContent(first, parentDir)
                val level = first.level
                TopicDocument(
                    tuples[index].id,
                    content,
                    roomMap[first.room]!!.id,
                    ObjectType.ROOM.name,
                    when (level) {
                        null, 0 -> roomMap[first.room]!!.id
                        else -> tuples[index - first.parent!!].id
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
        exposedDatabase.userDatabase.insertMediaRefs(topicId, ObjectType.TOPIC, mediaNames)
    }

    private suspend fun Backend.insertEncryptedTopicToRoom(
        parentDir: File,
        tika: Tika,
        roomMap: Map<String, Room>,
        privateRoomList: List<PresetTopic>,
        userList: Map<String, User>
    ) {
        val roomMembers = privateRoomList.mapNotNull {
            it.room
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
        val encryptedContents = privateRoomList.map {
            val (encryptedContent, aesBytes) = encryptData(getTopicContent(it, parentDir)).getOrThrow()
            EncryptedTopicTuple(encryptedContent, aesBytes, SnowflakeFactory.nextId(), it)
        }
        val encryptedKeys = encryptedContents.flatMap {
            val topic = it.presetTopic
            val id = it.id
            val aesBytes = it.aesKey
            roomMembers[topic.room]!!.map { (derPublicKey, uid) ->
                Triple(id, eciesEncrypt(derPublicKey, aesBytes).getOrThrow(), uid)
            }
        }
        insertRoomTopic(encryptedContents.map {
            Triple(it.presetTopic, it.encryptedContent, true)
        }, userList, roomMap)
        EncryptedKeys.batchInsert(encryptedKeys) { (topicId, b, uid) ->
            this[EncryptedKeys.topicId] = topicId
            this[EncryptedKeys.encryptedAes] = ExposedBlob(b)
            this[EncryptedKeys.uid] = uid
        }
        privateRoomList.forEach { topic ->
            val room = roomMap[topic.room]
            if (room != null) {
                val content = this@AddPreset.getTopicContent(topic, parentDir)
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
                InsertTopicTuple(addTopic, index, 0, id, addTopic.content.encodeToByteArray(), false)
            } else {
                InsertTopicTuple(addTopic, index, level, id, addTopic.content.encodeToByteArray(), false)
            }
        }

        Topics.batchInsert(topicTuples) {
            val first = it.topic
            val id = it.id
            val level = it.level
            val index = it.originalIndex
            this[Topics.id] = id
            this[Topics.author] = userList[first.author]!!.id
            this[Topics.createdTime] = now()
            this[Topics.rootId] = rootId(first)
            this[Topics.rootType] = rootType
            this[Topics.parentId] =
                if (level == 0) rootId(first) else topicTuples[index - first.parent!!].id
            this[Topics.parentType] = if (level == 0) rootType else ObjectType.TOPIC
            this[Topics.content] = ExposedBlob(it.content)
            this[Topics.isEncrypted] = it.isEncrypted
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
