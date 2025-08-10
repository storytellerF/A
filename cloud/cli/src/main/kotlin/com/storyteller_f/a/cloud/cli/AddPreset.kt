package com.storyteller_f.a.cloud.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.InsertCommunityTuple
import com.storyteller_f.a.backend.core.InsertTopicTuple
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.getMediaInfoList
import com.storyteller_f.a.backend.service.index.TopicDocument
import com.storyteller_f.a.backend.service.object_storage.uploadFilesAfterDetectContentTypeAndDimension
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.eciesEncrypt
import com.storyteller_f.shared.encryptData
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.obj.PresetRoom
import com.storyteller_f.shared.obj.PresetTopic
import com.storyteller_f.shared.obj.PresetUser
import com.storyteller_f.shared.obj.PresetValue
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.Tuple4
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
            try {
                when (val type = presetValue.type) {
                    "community" -> connected.addCommunities(presetValue, parentDir)
                    "user" -> connected.addUsers(presetValue, parentDir)
                    "topic" -> connected.addTopics(presetValue, parentDir)
                    "room" -> connected.addRooms(presetValue, parentDir)
                    "file" -> connected.addFiles(presetValue, parentDir)
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

    private suspend fun Backend.addFiles(presetValue: PresetValue, parentDir: File?) {
        val files = presetValue.fileData ?: return
        Napier.i {
            "files count ${presetValue.fileData?.size}"
        }
        val userMap =
            exposedDatabase.userDatabase.getRawUsers(ObjectListFetch.AidListFetch(files.map {
                it.owner
            }.distinct())).getOrThrow().associate {
                it.user.aid!! to it.user
            }
        val lists = HttpClient(OkHttp).use { client ->
            files.map {
                it.paths.mapNotNull { p ->
                    if (p.endsWith("download")) {
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
                } to it.owner
            }
        }
        lists.forEach { (it, owner) ->
            uploadFilesAfterDetectContentTypeAndDimension(it.map { path ->
                UploadPack(path, path.name, userMap[owner]!!.id, ObjectType.USER, path.length())
            }).getOrThrow()
        }
    }

    private suspend fun Backend.addRooms(presetValue: PresetValue, parentDir: File?) {
        val l = presetValue.roomData ?: return
        Napier.i {
            "rooms count ${presetValue.roomData?.size}"
        }
        val (roomList, membersList) = getRoomsData(l, parentDir)
        exposedDatabase.cliDatabase.batchAddRooms(roomList, membersList)
    }

    private suspend fun Backend.addTopics(presetValue: PresetValue, parentDir: File) {
        Napier.i {
            "topics count ${presetValue.topicData?.size}"
        }
        val data = presetValue.topicData!!
        val userMap =
            exposedDatabase.userDatabase.getRawUsers(ObjectListFetch.AidListFetch(data.map {
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

    private suspend fun Backend.addUsers(presetValue: PresetValue, parentDir: File?) {
        val userList = presetValue.userData ?: return
        Napier.i {
            "users count ${presetValue.userData?.size}"
        }
        val users = getUserData(userList, parentDir)
        exposedDatabase.cliDatabase.batchAddUser(users)
    }

    private suspend fun Backend.addCommunities(presetValue: PresetValue, parentDir: File?) {
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
                val path = File(parentDir, icon)
                uploadFilesAfterDetectContentTypeAndDimension(
                    listOf(
                        UploadPack(
                            path,
                            "community-icon.${path.extension}",
                            id,
                            ObjectType.COMMUNITY,
                            path.length(),
                        )
                    )
                ).getOrThrow().first()?.id
            }
            val fontMedia = if (font == null) {
                null
            } else {
                backend.getMediaInfoList(listOf("100/$font")).getOrThrow()?.firstOrNull()?.id
            }
            InsertCommunityTuple(it, iconMedia, id, fontMedia)
        }
        val userMap =
            exposedDatabase.userDatabase.getRawUsers(ObjectListFetch.AidListFetch(data.flatMap {
                it.community.users.orEmpty() + (it.community.admin ?: "System")
            }.distinct())).getOrThrow().associate {
                it.user.aid to it.user
            }
        exposedDatabase.cliDatabase.batchAddCommunities(data.map {
            Community(
                it.id,
                now(),
                it.community.id,
                it.community.name,
                userMap[it.community.admin ?: "System"]!!.id,
                it.icon,
                fontId = it.font,
            )
        }, data.map {
            it.id to it.community.users?.map { s ->
                userMap[s]!!.id
            }.orEmpty() + userMap["System"]!!.id
        }, data.map {
            Tuple4(userMap[it.community.admin ?: "System"]!!.id, it.id, it.community.id, List(3) {
                SnowflakeFactory.nextId()
            })
        })
    }

    private suspend fun Backend.addTopics(
        list: List<PresetTopic>,
        userMap: Map<String, User>,
        parentDir: File,
        objectType: ObjectType,
    ) {
        val communityMap =
            exposedDatabase.communityDatabase.getRawCommunities(ObjectListFetch.AidListFetch(list.mapNotNull {
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
            if (parent == null || parent == 0 || level == null || level == 0) {
                InsertTopicTuple(addTopic, index, 0, id, content, false, rootId)
            } else {
                InsertTopicTuple(addTopic, index, level, id, content, false, rootId)
            }
        }
        exposedDatabase.cliDatabase.batchAddTopics(tuples, userMap, objectType)
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
    }

    private suspend fun Backend.getUserData(
        userList: List<PresetUser>,
        parentDir: File?,
    ): List<User> {
        return userList.map {
            val id = it.id ?: SnowflakeFactory.nextId()
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
                    listOf(
                        UploadPack(
                            path,
                            "avatar.${path.extension}",
                            id,
                            ObjectType.USER,
                            path.length()
                        )
                    )
                ).getOrThrow().first()
                UserPresetTuple(it, mediaInfo?.id, derPublicKey, ad, id)
            }
        }.map {
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
                AlgoType.P256
            )
        }
    }

    private suspend fun Backend.getRoomsData(
        l: List<PresetRoom>,
        parentDir: File?,
    ): Pair<List<Room>, List<Pair<List<PrimaryKey>, PrimaryKey>>> {
        val data = l.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            if (icon == null) {
                Triple(it, null, id)
            } else {
                val path = File(parentDir, icon)
                val mediaInfo = uploadFilesAfterDetectContentTypeAndDimension(
                    listOf(
                        UploadPack(
                            path,
                            "room-icon.${path.extension}",
                            id,
                            ObjectType.ROOM,
                            path.length(),
                        )
                    )
                ).getOrThrow().first()
                Triple(it, mediaInfo?.id, id)
            }
        }

        val userMap = exposedDatabase.userDatabase.getRawUsers(
            ObjectListFetch.AidListFetch(l.flatMap {
                it.users + it.admin
            }.distinct())
        ).getOrThrow().associate {
            it.user.aid to it.user
        }

        val communityMap =
            exposedDatabase.communityDatabase.getRawCommunities(ObjectListFetch.AidListFetch(l.mapNotNull {
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

    private suspend fun Backend.addTopicsIntoRoom(
        list: List<PresetTopic>,
        userMap: Map<String, User>,
        parentDir: File,
    ) {
        val roomMap =
            exposedDatabase.roomData.getRoomList(ObjectListFetch.AidListFetch(list.mapNotNull {
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
        publicRoomList: List<PresetTopic>,
    ) {
        val tuples = publicRoomList.mapIndexed { index, topic ->
            val addTopic = topic to topic.content.encodeToByteArray()
            val id = SnowflakeFactory.nextId()
            val level = addTopic.first.level
            val parent = addTopic.first.parent
            val content = getTopicContent(addTopic.first, parentDir).encodeToByteArray()
            val rootId = roomMap[topic.room]!!.id
            if (parent == null || parent == 0 || level == null || level == 0) {
                InsertTopicTuple(addTopic.first, index, 0, id, content, false, rootId)
            } else {
                InsertTopicTuple(addTopic.first, index, level, id, content, false, rootId)
            }
        }
        exposedDatabase.cliDatabase.batchAddTopics(tuples, userMap, ObjectType.ROOM)
        topicSearchService.saveDocument(
            publicRoomList.mapIndexed { index, first ->
                val level = first.level
                TopicDocument(
                    tuples[index].id,
                    tuples[index].content.decodeToString(),
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
        publicRoomList.forEachIndexed { i, topic ->
            uploadTopicMedias(parentDir, userMap, tuples[i].id, topic)
        }
    }

    private suspend fun Backend.uploadTopicMedias(
        parentDir: File,
        userMap: Map<String, User>,
        topicId: PrimaryKey,
        presetTopic: PresetTopic,
    ) {
        val content = getTopicContent(presetTopic, parentDir)
        val mediaLink = extractMarkdownMediaLink(content)
        val mediaNames = mediaLink.map {
            userMap[presetTopic.author]!!.id to it
        }
        uploadFilesAfterDetectContentTypeAndDimension(mediaNames.map { (author, pic) ->
            val path = File(parentDir, "medias/topics/$pic")
            UploadPack(path, pic, author, ObjectType.USER, path.length())
        }).getOrThrow()
        exposedDatabase.mediaDatabase.insertMediaRefs(topicId, ObjectType.TOPIC, mediaNames)
    }

    private suspend fun Backend.insertEncryptedTopicToRoom(
        parentDir: File,
        roomMap: Map<String, Room>,
        privateRoomList: List<PresetTopic>,
        userMap: Map<String, User>,
    ) {
        val distinct = privateRoomList.mapNotNull {
            it.room
        }.distinct()
        val roomMembers = exposedDatabase.cliDatabase.getAllMembers(distinct).getOrThrow().groupBy {
            it.third
        }
        val encryptedContents = privateRoomList.map {
            val (encryptedContent, aesBytes) = encryptData(
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
                Triple(id, eciesEncrypt(derPublicKey, aesBytes).getOrThrow(), uid)
            }
        }
        val tuples = encryptedContents.mapIndexed { index, tuple ->
            val id = tuple.id
            val level = tuple.presetTopic.level
            val parent = tuple.presetTopic.parent
            val content = tuple.encryptedContent
            val rootId = roomMap[tuple.presetTopic.room]!!.id
            if (parent == null || parent == 0 || level == null || level == 0) {
                InsertTopicTuple(tuple.presetTopic, index, 0, id, content, true, rootId)
            } else {
                InsertTopicTuple(tuple.presetTopic, index, level, id, content, true, rootId)
            }
        }
        exposedDatabase.cliDatabase.batchAddEncryptTopics(tuples, userMap, roomMap, encryptedKeys)

        privateRoomList.forEach { topic ->
            val room = roomMap[topic.room]
            if (room != null) {
                val content = getTopicContent(topic, parentDir)
                uploadFilesAfterDetectContentTypeAndDimension(extractMarkdownMediaLink(content).map {
                    val path = File(parentDir, "medias/topics/$it")
                    UploadPack(path, it, room.id, ObjectType.ROOM, path.length())
                }).getOrThrow()
            }
        }
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

    file.parentFile?.mkdirs()

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
