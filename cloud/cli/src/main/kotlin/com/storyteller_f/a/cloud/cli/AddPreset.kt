package com.storyteller_f.a.cloud.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.InsertCommunityTuple
import com.storyteller_f.a.backend.core.InsertRoomTuple
import com.storyteller_f.a.backend.core.InsertTopicTuple
import com.storyteller_f.a.backend.core.ObjectListFetch.AidListFetch
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.core.types.buildMemberForNotificationRoom
import com.storyteller_f.a.backend.core.types.buildUserNotificationRoom
import com.storyteller_f.a.cloud.core.service.getFileInfoList
import com.storyteller_f.a.cloud.core.service.tryUploadFiles
import com.storyteller_f.shared.encryptDataByAES
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.obj.PresetRoom
import com.storyteller_f.shared.obj.PresetTitle
import com.storyteller_f.shared.obj.PresetTopic
import com.storyteller_f.shared.obj.PresetUser
import com.storyteller_f.shared.obj.PresetValue
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.system.exitProcess

class EncryptedTopicTuple(
    val encryptedContent: ByteArray,
    val aesKey: ByteArray,
    val id: PrimaryKey,
    val presetTopic: PresetTopic,
)

data class UserPresetTuple(
    val presetUser: PresetUser,
    val pic: PrimaryKey?,
    val publicKey: String,
    val address: String,
    val id: PrimaryKey,
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
        val jsonFile = File(jsonFilePath)

        val presetValue = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<PresetValue>(jsonFile.readText())

        val parentDir = jsonFile.parentFile.canonicalFile
        runBlocking {
            when (val type = presetValue.type) {
                "community" -> connected.addCommunities(presetValue, parentDir)
                "user" -> connected.addUsers(presetValue, parentDir)
                "topic" -> connected.addTopics(presetValue, parentDir)
                "room" -> connected.addRooms(presetValue, parentDir)
                "file" -> connected.addFiles(presetValue, parentDir)
                "title" -> connected.addTitles(presetValue)
                "panelAccount" -> connected.addPanels(presetValue, parentDir)
                else -> {
                    println("unrecognized type $type")
                    exitProcess(2)
                }
            }
            Napier.i {
                "add done ${jsonFile.canonicalPath}"
            }
        }
    }

    private suspend fun Backend.addPanels(presetValue: PresetValue, parentDir: File) {
        val accounts = presetValue.panelAccountData ?: return
        accounts.forEach {
            val id = SnowflakeFactory.nextId()
            val (derPublicKey, ad) = getPubKeyAndAddress(parentDir, it.privateKey)
            database.panelAccount.addPanelAccount(
                PanelAccount(id, it.name, PassType.RAW, AlgoType.P256, derPublicKey, ad)
            ).getOrThrow()
        }
    }

    private suspend fun Backend.addTitles(
        presetValue: PresetValue,
    ) {
        val titles = presetValue.titleData ?: return
        Napier.i {
            "titles count ${presetValue.titleData?.size}"
        }
        val userMap =
            database.user.getRawUsers(AidListFetch(titles.flatMap {
                buildList {
                    addAll(listOf(it.creator, it.uid))
                    if (it.scopeType == ObjectType.USER) {
                        add(it.scope)
                    }
                }
            }.distinct())).getOrThrow().associate {
                it.user.aid!! to it.user
            }
        val communityMap = database.community.getRawCommunities(
            AidListFetch(
                titles.filter {
                    it.scopeType == ObjectType.COMMUNITY
                }.map {
                    it.scope
                }.distinct()
            )
        ).getOrThrow().associate {
            it.community.aid to it.community
        }
        val roomMap = database.room.getRawRooms(
            AidListFetch(
                titles.filter {
                    it.scopeType == ObjectType.ROOM
                }.map {
                    it.scope
                }.distinct()
            )
        ).getOrThrow().associate {
            it.room.aid to it.room
        }
        batchAddTitle(titles, userMap, communityMap, roomMap)
    }

    private suspend fun Backend.batchAddTitle(
        titles: List<PresetTitle>,
        userMap: Map<String, User>,
        communityMap: Map<String, Community>,
        roomMap: Map<String, Room>
    ) {
        titles.forEach {
            val titleId = SnowflakeFactory.nextId()
            val topicId = SnowflakeFactory.nextId()
            val creatorUid = userMap[it.creator]!!.id
            val receiverUid = userMap[it.uid]!!.id
            val scopeId = when (it.scopeType) {
                ObjectType.USER -> {
                    userMap[it.scope]!!.id
                }

                ObjectType.COMMUNITY -> {
                    communityMap[it.scope]!!.id
                }

                ObjectType.ROOM -> roomMap[it.scope]!!.id
                else -> throw Exception("not support")
            }
            val title = Title(
                titleId, now(), it.name, creatorUid, receiverUid, it.type, scopeId, it.scopeType,
                TitleStatus.OK, titleId
            )
            val topic = Topic(
                topicId,
                now(),
                creatorUid,
                titleId,
                ObjectType.TITLE,
                titleId,
                ObjectType.TITLE,
                it.description.encodeToByteArray(),
                false,
                1
            )
            database.topic.createTitle(title, topic).getOrThrow()
        }
    }

    private suspend fun Backend.addFiles(presetValue: PresetValue, parentDir: File) {
        val files = presetValue.fileData ?: return
        Napier.i {
            "files count ${presetValue.fileData?.size}"
        }
        val userMap =
            database.user.getRawUsers(AidListFetch(files.map {
                it.owner
            }.distinct())).getOrThrow().associate {
                it.user.aid!! to it.user
            }
        val fileList = HttpClient(OkHttp).use { client ->
            files.map {
                it.paths.mapNotNull { p ->
                    downloadFileIfNeed(p, parentDir, client)
                } to it.owner
            }
        }
        fileList.forEach { (it, owner) ->
            uploadFile(userMap[owner]!!.id, ObjectType.USER, parentDir, it.map {
                it.toRelativeString(parentDir)
            })
        }
    }

    private suspend fun downloadFileIfNeed(
        p: String,
        parentDir: File?,
        client: HttpClient
    ): File? = if (p.endsWith("download")) {
        val path = File(parentDir, p)
        val lines = path.readLines()
        if (lines.size >= 3) {
            val name = lines.first()
            val link = lines[1]
            val hash = lines[2]
            val realPath = File(parentDir, "download/$name")
            if (realPath.exists()) {
                if (hash.startsWith("sha256:")) {
                    val calculatedSha = sha256File(realPath)
                    val hashValue = hash.removePrefix("sha256:")
                    Napier.i {
                        "calculated $name sha $calculatedSha, real $hashValue"
                    }
                    if (calculatedSha != hashValue) {
                        downloadWithResume(link, realPath, client)
                    }
                }
            } else {
                downloadWithResume(link, realPath, client)
            }

            realPath
        } else {
            null
        }
    } else {
        File(parentDir, p)
    }

    private suspend fun Backend.addRooms(presetValue: PresetValue, parentDir: File) {
        val presetRooms = presetValue.roomData ?: return
        Napier.i {
            "rooms count ${presetValue.roomData?.size}"
        }
        val userMap = getRoomUserMap(presetRooms)
        val communityMap = getRoomCommunityMap(presetRooms)
        val data = presetRooms.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            val s = if (icon == null) {
                null
            } else {
                uploadFile(id, ObjectType.ROOM, parentDir, listOf(icon))?.id
            }
            InsertRoomTuple(it, s, id, now())
        }
        val memberList = data.flatMap {
            (it.room.users.map { s ->
                userMap[s]!!.id
            } + userMap[it.room.admin]!!.id).distinct().map { uid ->
                Member(
                    SnowflakeFactory.nextId(),
                    uid,
                    it.id,
                    ObjectType.ROOM,
                    it.createdTime,
                    MemberStatus.JOINED,
                    it.createdTime
                )
            }
        }
        val roomList = data.map {
            val presetRoom = it.room
            Room(
                it.id,
                it.createdTime,
                presetRoom.id,
                presetRoom.name,
                userMap[presetRoom.admin]!!.id,
                it.icon,
                communityMap[presetRoom.community]?.id
            )
        }
        database.admin.batchAddRooms(roomList, memberList)
        roomSearchService.saveDocument(roomList.map {
            RoomDocument.fromRoom(it)
        })
    }

    private suspend fun Backend.getRoomCommunityMap(l: List<PresetRoom>): Map<String, Community> =
        database.community.getRawCommunities(AidListFetch(l.mapNotNull {
            it.community
        }.distinct())).getOrThrow().associate {
            it.community.aid to it.community
        }

    private suspend fun Backend.getRoomUserMap(l: List<PresetRoom>): Map<String?, User> {
        val userMap = database.user.getRawUsers(
            AidListFetch(l.flatMap {
                it.users + it.admin
            }.distinct())
        ).getOrThrow().associate {
            it.user.aid to it.user
        }
        return userMap
    }

    private suspend fun Backend.addTopics(presetValue: PresetValue, parentDir: File) {
        Napier.i {
            "topics count ${presetValue.topicData?.size}"
        }
        val data = presetValue.topicData!!
        val userMap =
            database.user.getRawUsers(AidListFetch(data.map {
                it.author
            }.distinct())).getOrThrow().associate {
                it.user.aid!! to it.user
            }
        data.groupBy {
            when {
                it.community != null -> ObjectType.COMMUNITY
                it.room != null -> ObjectType.ROOM
                else -> ObjectType.USER
            }
        }.forEach { (objectType, list) ->
            if (objectType == ObjectType.ROOM) {
                addTopicsIntoRoom(list, userMap, parentDir)
            } else {
                addTopics(list, userMap, parentDir, objectType)
            }
        }
    }

    private suspend fun Backend.addUsers(presetValue: PresetValue, parentDir: File) {
        val userList = presetValue.userData ?: return
        Napier.i {
            "users count ${presetValue.userData?.size}"
        }
        val users = getUserData(userList, parentDir)
        database.admin.batchAddUser(users)
        val userMap =
            database.user.getRawUsers(AidListFetch(listOf("System"))).getOrThrow()
                .associate {
                    it.user.aid to it.user
                }
        val adminUid = userMap["System"]!!.id
        val realUser = users.filter {
            it.id > 1000
        }
        database.admin.batchAddRooms(realUser.map {
            buildUserNotificationRoom(it, adminUid)
        }, realUser.flatMap {
            buildMemberForNotificationRoom(it, adminUid)
        })
        userSearchService.saveDocument(users.map {
            UserDocument.fromUser(it)
        })
    }

    private suspend fun Backend.addCommunities(presetValue: PresetValue, parentDir: File) {
        val communityData = presetValue.communityData!!
        Napier.i {
            "communities count ${presetValue.communityData?.size}"
        }
        val data = communityData.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            val font = it.font
            val iconMedia = if (icon == null) {
                null
            } else {
                uploadFile(id, ObjectType.COMMUNITY, parentDir, listOf(icon))?.id
            }
            val fontMedia = if (font == null) {
                null
            } else {
                backend.getFileInfoList(listOf("100/$font")).getOrThrow()?.firstOrNull()?.id
            }
            InsertCommunityTuple(it, iconMedia, id, fontMedia, now())
        }
        val userMap =
            database.user.getRawUsers(AidListFetch(data.flatMap {
                it.community.users.orEmpty() + (it.community.admin ?: "System")
            }.distinct())).getOrThrow().associate {
                it.user.aid to it.user
            }
        val communities = data.map {
            Community(
                it.id,
                it.createdTime,
                it.community.id,
                it.community.name,
                userMap[it.community.getSafeAdmin()]!!.id,
                MemberPolicy.OPEN,
                it.icon,
                fontId = it.font,
            )
        }
        val memberList = data.flatMap {
            (it.community.users?.map { s ->
                userMap[s]!!.id
            }.orEmpty() + userMap[it.community.getSafeAdmin()]!!.id).map { uid ->
                Member(
                    SnowflakeFactory.nextId(),
                    uid,
                    it.id,
                    ObjectType.COMMUNITY,
                    it.createdTime,
                    MemberStatus.JOINED,
                    it.createdTime
                )
            }
        }
        database.admin.batchAddCommunities(communities, memberList)
        communitySearchService.saveDocument(communities.map {
            CommunityDocument.fromCommunity(it)
        })
    }

    private suspend fun Backend.addTopics(
        list: List<PresetTopic>,
        userMap: Map<String, User>,
        parentDir: File,
        objectType: ObjectType,
    ) {
        val communityMap =
            database.community.getRawCommunities(AidListFetch(list.mapNotNull {
                it.community
            })).getOrThrow().associate {
                it.community.aid to it.community.id
            }
        val tuples = list.mapIndexed { index, addTopic ->
            val id = SnowflakeFactory.nextId()
            val level = addTopic.level
            val parent = addTopic.parent
            val content = getTopicContent(addTopic, parentDir).encodeToByteArray()
            val rootId = if (objectType == ObjectType.USER) {
                userMap[addTopic.author]!!.id
            } else {
                communityMap[addTopic.community]!!
            }
            InsertTopicTuple(
                addTopic,
                index,
                if (parent == null || parent == 0 || level == null || level == 0) {
                    0
                } else {
                    level
                },
                id,
                content,
                false,
                rootId
            )
        }
        database.admin.batchAddTopics(tuples, userMap, objectType).getOrThrow()
        topicSearchService.saveDocument(
            tuples.mapIndexed { index, topicTuple ->
                val level = topicTuple.level
                TopicDocument(
                    topicTuple.id,
                    topicTuple.content.decodeToString(),
                    topicTuple.rootId,
                    objectType.name,
                    when (level) {
                        0 -> topicTuple.rootId
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
        tuples.forEach { topicTuple ->
            uploadTopicMedias(parentDir, userMap, topicTuple.id, topicTuple.topic)
        }
        batchAddSubscriptions(tuples, userMap)
    }

    private suspend fun Backend.getUserData(
        userList: List<PresetUser>,
        parentDir: File,
    ): List<User> {
        return userList.map {
            val id = it.id ?: SnowflakeFactory.nextId()
            val (derPublicKey, ad) = getPubKeyAndAddress(parentDir, it.privateKey)
            val icon = it.icon
            val p = if (icon == null) {
                null
            } else {
                uploadFile(id, ObjectType.USER, parentDir, listOf(icon))?.id
            }
            UserPresetTuple(it, p, derPublicKey, ad, id)
        }.map {
            val notificationId = SnowflakeFactory.nextId()
            User(
                it.presetUser.aid,
                it.publicKey,
                it.address,
                it.pic,
                it.presetUser.name.takeIf { s -> s.isNotBlank() } ?: nameService.parse(it.id),
                it.id,
                now(),
                0,
                PassType.RAW,
                AlgoType.P256,
                notificationId
            )
        }
    }

    private suspend fun getPubKeyAndAddress(
        parentDir: File,
        privatePath: String
    ): Pair<String, String> {
        return getAlgo().run {
            val derPublicKey =
                getDerPublicKeyFromPrivateKey(
                    File(parentDir, privatePath).readText().replace("\r\n", "\n")
                ).getOrThrow()
            val ad = calcAddress(derPublicKey).getOrThrow()
            Pair(derPublicKey, ad)
        }
    }

    private suspend fun Backend.addTopicsIntoRoom(
        list: List<PresetTopic>,
        userMap: Map<String, User>,
        parentDir: File,
    ) {
        val roomMap =
            database.room.getRoomList(AidListFetch(list.mapNotNull {
                it.room
            })).getOrThrow().associateBy { it.aid }
        insertEncryptedTopicToRoom(parentDir, roomMap, list.filter {
            roomMap[it.room]?.communityId == null
        }, userMap)
        insertUnEncryptedTopicToRoom(parentDir, roomMap, userMap, list.filter {
            roomMap[it.room]?.communityId != null
        })
    }

    private suspend fun Backend.insertUnEncryptedTopicToRoom(
        parentDir: File,
        roomMap: Map<String, Room>,
        userMap: Map<String, User>,
        topicList: List<PresetTopic>,
    ) {
        val tuples = topicList.mapIndexed { index, topic ->
            val id = SnowflakeFactory.nextId()
            val level = topic.level
            val parent = topic.parent
            val content = getTopicContent(topic, parentDir).encodeToByteArray()
            val rootId = roomMap[topic.room]!!.id
            val l = if (parent == null || parent == 0 || level == null || level == 0) {
                0
            } else {
                level
            }
            InsertTopicTuple(topic, index, l, id, content, false, rootId)
        }
        database.admin.batchAddTopics(tuples, userMap, ObjectType.ROOM).getOrThrow()
        topicSearchService.saveDocument(
            tuples.mapIndexed { index, topicTuple ->
                val topic = topicTuple.topic
                val level = topic.level
                TopicDocument(
                    topicTuple.id,
                    topicTuple.content.decodeToString(),
                    roomMap[topic.room]!!.id,
                    ObjectType.ROOM.name,
                    when (level) {
                        null, 0 -> roomMap[topic.room]!!.id
                        else -> tuples[index - topic.parent!!].id
                    },
                    (if (level == 0) ObjectType.ROOM else ObjectType.TOPIC).name,
                    userMap[topic.author]!!.id
                )
            }
        ).getOrThrow()
        tuples.forEachIndexed { i, topicTuple ->
            uploadTopicMedias(parentDir, userMap, topicTuple.id, topicTuple.topic)
        }
        batchAddSubscriptions(tuples, userMap)
    }

    private suspend fun Backend.uploadTopicMedias(
        parentDir: File,
        userMap: Map<String, User>,
        topicId: PrimaryKey,
        presetTopic: PresetTopic,
    ) {
        val content = getTopicContent(presetTopic, parentDir)
        val mediaLink = extractMarkdownMediaLink(content)
        val author = userMap[presetTopic.author]!!.id
        uploadFile(author, ObjectType.USER, parentDir, mediaLink.map {
            "medias/topics/$it"
        })
        database.file.insertFileRefs(topicId, ObjectType.TOPIC, mediaLink.map {
            author to it
        })
    }

    private suspend fun Backend.insertEncryptedTopicToRoom(
        parentDir: File,
        roomMap: Map<String, Room>,
        topicList: List<PresetTopic>,
        userMap: Map<String, User>,
    ) {
        val roomAids = topicList.mapNotNull {
            it.room
        }.distinct()
        val roomMembers =
            database.admin.getAllMembers(roomAids).getOrThrow().groupBy {
                it.third
            }
        val encryptedContents = topicList.map {
            val (encryptedContent, aesBytes) = encryptDataByAES(
                getTopicContent(
                    it,
                    parentDir
                )
            ).getOrThrow()
            EncryptedTopicTuple(encryptedContent, aesBytes, SnowflakeFactory.nextId(), it)
        }
        val encryptedKeys = encryptedContents.flatMap {
            val topic = it.presetTopic
            val id = it.id
            val aesBytes = it.aesKey
            roomMembers[topic.room]!!.map { (derPublicKey, uid) ->
                Triple(id, getAlgo().kemEncrypt(derPublicKey, aesBytes).getOrThrow(), uid)
            }
        }
        val tuples = encryptedContents.mapIndexed { index, tuple ->
            val id = tuple.id
            val level = tuple.presetTopic.level
            val parent = tuple.presetTopic.parent
            val content = tuple.encryptedContent
            val rootId = roomMap[tuple.presetTopic.room]!!.id
            val level1 = if (parent == null || parent == 0 || level == null || level == 0) {
                0
            } else {
                level
            }
            InsertTopicTuple(tuple.presetTopic, index, level1, id, content, true, rootId)
        }
        database.admin.batchAddTopics(tuples, userMap, ObjectType.ROOM).getOrThrow()
        database.admin.batchAddEncryptTopicKeys(encryptedKeys).getOrThrow()
        tuples.forEach { topicTuple ->
            val room = roomMap[topicTuple.topic.room]
            if (room != null) {
                val content = getTopicContent(topicTuple.topic, parentDir)
                uploadFile(
                    room.id,
                    ObjectType.ROOM,
                    parentDir,
                    extractMarkdownMediaLink(content).map {
                        "medias/topics/$it"
                    }
                )
            }
        }
        batchAddSubscriptions(tuples, userMap)
    }

    private suspend fun Backend.batchAddSubscriptions(
        tuples: List<InsertTopicTuple>,
        userMap: Map<String, User>
    ) {
        database.admin.batchAddSubscription(tuples.map {
            UserSubscription(
                it.id,
                userMap[it.topic.author]!!.id,
                it.id,
                ObjectType.TOPIC,
                now()
            )
        }).getOrThrow()
    }

    suspend fun Backend.uploadFile(
        id: PrimaryKey,
        type: ObjectType,
        parentDir: File,
        p: List<String>
    ): FileInfo? {
        if (p.isEmpty()) return null
        return tryUploadFiles(
            id,
            type,
            p.map {
                val path = File(parentDir, it)
                val name = path.name
                UploadPack(
                    path,
                    name,
                    path.length(),
                    "$id/$name"
                )
            }
        ).getOrThrow().first()
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

suspend fun downloadWithResume(
    url: String,
    file: File,
    client: HttpClient
) {
    val downloadedSize = if (file.exists()) file.length() else 0L

    val response: HttpResponse = client.get(url) {
        if (downloadedSize > 0) {
            header("Range", "bytes=$downloadedSize-")
        }
    }

    val status = response.status.value
    println("HTTP status: $status")

    val body = response.bodyAsChannel()

    file.parentFile!!.mkdirs()

    RandomAccessFile(file, "rw").use { raf ->
        raf.seek(downloadedSize)
        while (!body.isClosedForRead) {
            val packet = body.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
            while (!packet.exhausted()) {
                val bytes = packet.readByteArray()
                raf.write(bytes)
            }
        }
    }
}

fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { fis ->
        val buffer = ByteArray(8192)
        var read = fis.read(buffer)
        while (read != -1) {
            digest.update(buffer, 0, read)
            read = fis.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
