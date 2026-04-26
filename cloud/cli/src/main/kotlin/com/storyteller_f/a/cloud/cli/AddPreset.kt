package com.storyteller_f.a.cloud.cli

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.InsertCommunityTuple
import com.storyteller_f.a.backend.core.InsertRoomTuple
import com.storyteller_f.a.backend.core.InsertTopicTuple
import com.storyteller_f.a.backend.core.ObjectListFetch.AidListFetch
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.MemberDocument
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.FileRef
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.core.types.buildMemberForNotificationRoom
import com.storyteller_f.a.backend.core.types.buildUserNotificationRoom
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.a.cloud.core.service.addUserLog
import com.storyteller_f.a.cloud.core.service.getFileInfoList
import com.storyteller_f.a.cloud.core.service.tryUploadFiles
import com.storyteller_f.shared.Type2Algo
import com.storyteller_f.shared.encryptDataByAES
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.FontSettings
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.TitleWorkStatus
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.PresetCommunity
import com.storyteller_f.shared.obj.PresetRoom
import com.storyteller_f.shared.obj.PresetTitle
import com.storyteller_f.shared.obj.PresetTopic
import com.storyteller_f.shared.obj.PresetUser
import com.storyteller_f.shared.obj.PresetValue
import com.storyteller_f.shared.obj.ob
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
    val algoType: AlgoType
)

internal data class DownloadConfig(
    val name: String,
    val link: String,
    val hash: String,
    val excludeArchiveEntries: List<String> = emptyList(),
    val includeArchiveEntries: List<String> = emptyList()
)

private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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
            connected.applyPresetByValue(presetValue, parentDir)
            Napier.i {
                "add done ${jsonFile.canonicalPath}"
            }
        }
    }

    suspend fun Backend.applyPresetByValue(presetValue: PresetValue, parentDir: File) {
        when (val type = presetValue.type) {
            "community" -> addCommunities(presetValue, parentDir)
            "user" -> addUsers(presetValue, parentDir)
            "topic" -> addTopics(presetValue, parentDir)
            "room" -> addRooms(presetValue, parentDir)
            "file" -> addFiles(presetValue, parentDir)
            "title" -> addTitles(presetValue)
            "panelAccount" -> addPanels(presetValue, parentDir)
            else -> {
                Napier.e { "unrecognized type $type" }
                exitProcess(2)
            }
        }
    }

    private suspend fun Backend.addPanels(presetValue: PresetValue, parentDir: File) {
        val accounts = presetValue.panelAccountData ?: return
        accounts.forEach {
            val id = SnowflakeFactory.nextId()
            val (derPublicKey, ad) = getPubKeyAndAddress(parentDir, it.privateKey, AlgoType.P256)
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
            ),
            null
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
                TitleWorkStatus.OK, topicId, null
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
            // 为创建者添加 user log
            addUserLog(creatorUid, UserLogType.CREATE, titleId ob ObjectType.TITLE).getOrThrow()
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
    ): File? = downloadPresetFileIfNeed(p, parentDir, client)

    private suspend fun Backend.addRooms(presetValue: PresetValue, parentDir: File) {
        val presetRooms = presetValue.roomData ?: return
        Napier.i {
            "rooms count ${presetValue.roomData?.size}"
        }
        val userMap = getRoomUserMap(presetRooms)
        val communityMap = getRoomCommunityMap(presetRooms)
        val fileRefs = mutableListOf<Triple<FileInfo, String, PrimaryKey>>()
        val data = presetRooms.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            val s = if (icon == null) {
                null
            } else {
                uploadRoomIcon(id, parentDir, icon, fileRefs, it)
            }
            InsertRoomTuple(it, s, id, now())
        }
        database.file.insertFileRefs(fileRefs.map { (fileInfo, admin, id) ->
            FileRef(
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                objectId = id,
                objectType = ObjectType.ROOM,
                author = userMap[admin]!!.id,
                mediaName = fileInfo.name,
                fileId = fileInfo.id,
            )
        }).getOrThrow()
        val memberList = getRoomMembers(data, userMap)
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
        }).getOrThrow()
        // 为加入房间的成员添加 user log
        memberList.forEach { member ->
            addUserLog(member.uid, UserLogType.JOIN, member.objectId ob member.objectType).getOrThrow()
        }
        addMemberDocuments(memberList, userMap, roomMap = roomList.associateBy { it.id })
    }

    private suspend fun getRoomMembers(
        data: List<InsertRoomTuple>,
        userMap: Map<String?, User>
    ): List<Member> {
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
        return memberList
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
        val (tuples, fileRefs) = getUserData(userList, parentDir)
        val users = tuples.map {
            getUserFromTuple(it, parentDir)
        }
        database.admin.batchAddUser(users)
        database.file.insertFileRefs(fileRefs.map { (uploadFile, id) ->
            FileRef(
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                objectId = id,
                objectType = ObjectType.USER,
                author = id,
                mediaName = uploadFile.name,
                fileId = uploadFile.id,
            )
        }).getOrThrow()
        // 为每个创建的用户添加 user log
        users.forEach { user ->
            addUserLog(user.id, UserLogType.SIGN_UP, user.id ob ObjectType.USER).getOrThrow()
        }
        val userMap = database.user.getRawUsers(AidListFetch(listOf("System"))).getOrThrow()
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
        val (data, fileRefs) = getCommunityTuples(communityData, parentDir)
        val userMap = database.user.getRawUsers(AidListFetch(data.flatMap {
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
                fontSettings = it.fontSettings,
            )
        }
        val memberList = getCommunityMembers(data, userMap)
        database.admin.batchAddCommunities(communities, memberList)
        communitySearchService.saveDocument(communities.map {
            CommunityDocument.fromCommunity(it)
        }).getOrThrow()

        // Construct FileRefs here
        val finalFileRefs = fileRefs.map { (fileInfo, communityId, adminAid) ->
            FileRef(
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                objectId = communityId,
                objectType = ObjectType.COMMUNITY,
                author = userMap[adminAid]!!.id,
                mediaName = fileInfo.name,
                fileId = fileInfo.id,
            )
        }
        database.file.insertFileRefs(finalFileRefs).getOrThrow()

        // 为社区创建者添加 user log
        communities.forEach { community ->
            addUserLog(community.owner, UserLogType.CREATE, community.id ob ObjectType.COMMUNITY).getOrThrow()
        }
        // 为加入的成员添加 user log
        memberList.forEach { member ->
            addUserLog(member.uid, UserLogType.JOIN, member.objectId ob member.objectType).getOrThrow()
        }
        addMemberDocuments(memberList, userMap, communityMap = communities.associateBy { it.id })
    }

    private suspend fun getCommunityMembers(
        data: List<InsertCommunityTuple>,
        userMap: Map<String?, User>
    ): List<Member> {
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
        return memberList
    }

    private suspend fun Backend.getCommunityTuples(
        communityData: List<PresetCommunity>,
        parentDir: File
    ): Pair<List<InsertCommunityTuple>, List<Triple<FileInfo, PrimaryKey, String>>> {
        val fileRefs = mutableListOf<Triple<FileInfo, PrimaryKey, String>>()
        val tuples = communityData.map {
            val id = SnowflakeFactory.nextId()
            val icon = it.icon
            val font = it.font
            val iconMedia = if (icon == null) {
                null
            } else {
                val uploadFile = uploadFile(id, ObjectType.COMMUNITY, parentDir, listOf(icon)).first()
                fileRefs.add(Triple(uploadFile, id, it.getSafeAdmin()))
                uploadFile.id
            }
            val fontSettings = if (font == null) {
                null
            } else {
                val fontMediaId = backend.getFileInfoList(listOf("100/$font")).getOrThrow()?.firstOrNull()?.id
                fontMediaId?.let { fontId -> FontSettings(contentFontId = fontId) }
            }
            InsertCommunityTuple(it, iconMedia, id, fontSettings, now())
        }
        return tuples to fileRefs
    }

    private suspend fun Backend.addMemberDocuments(
        memberList: List<Member>,
        userMap: Map<String?, User>,
        communityMap: Map<PrimaryKey, Community> = emptyMap(),
        roomMap: Map<PrimaryKey, Room> = emptyMap()
    ) {
        memberSearchService.saveDocument(
            memberList.mapNotNull { member ->
                val user = userMap.values.find { it.id == member.uid }
                user?.let {
                    val objectName = when (member.objectType) {
                        ObjectType.COMMUNITY -> communityMap[member.objectId]?.name
                        ObjectType.ROOM -> roomMap[member.objectId]?.name
                        else -> null
                    }!!
                    MemberDocument.fromUserInfo(
                        id = member.id,
                        userInfo = it.toUserInfo(),
                        objectId = member.objectId,
                        objectType = member.objectType,
                        objectName = objectName
                    )
                }
            }
        ).getOrThrow()
    }

    private suspend fun Backend.addTopics(
        list: List<PresetTopic>,
        userMap: Map<String, User>,
        parentDir: File,
        objectType: ObjectType
    ) {
        val communityMap = database.community.getRawCommunities(AidListFetch(list.mapNotNull {
            it.community
        })).getOrThrow().associate {
            it.community.aid to it.community.id
        }
        val tuples = getTopicTuples(list, parentDir, objectType, userMap, communityMap)
        database.admin.batchAddTopics(tuples, userMap, objectType).getOrThrow()
        topicSearchService.saveDocument(getDocumentsFromTuples(tuples, objectType, userMap)).getOrThrow()
        tuples.forEach { topicTuple ->
            uploadTopicMedias(parentDir, userMap, topicTuple.id, topicTuple.topic)
        }
        // 为 topic 作者添加 user log
        tuples.forEach { tuple ->
            val authorId = userMap[tuple.topic.author]!!.id
            addUserLog(authorId, UserLogType.CREATE, tuple.id ob ObjectType.TOPIC).getOrThrow()
        }
        batchAddSubscriptions(tuples, userMap)
    }

    private fun getDocumentsFromTuples(
        tuples: List<InsertTopicTuple>,
        objectType: ObjectType,
        userMap: Map<String, User>
    ): List<TopicDocument> = tuples.mapIndexed { index, topicTuple ->
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

    private suspend fun getTopicTuples(
        list: List<PresetTopic>,
        parentDir: File,
        objectType: ObjectType,
        userMap: Map<String, User>,
        communityMap: Map<String, PrimaryKey>
    ): List<InsertTopicTuple> = list.mapIndexed { index, addTopic ->
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

    private suspend fun Backend.getUserData(
        userList: List<PresetUser>,
        parentDir: File,
    ): Pair<List<UserPresetTuple>, MutableList<Pair<FileInfo, PrimaryKey>>> {
        val fileRefs = mutableListOf<Pair<FileInfo, PrimaryKey>>()
        val users = userList.map {
            val id = it.id ?: SnowflakeFactory.nextId()
            val algoType = it.algoType?.let { value -> AlgoType.valueOf(value) } ?: AlgoType.P256
            val (derPublicKey, ad) = getPubKeyAndAddress(parentDir, it.privateKey, algoType)
            val icon = it.icon
            val p = if (icon == null) {
                null
            } else {
                uploadUserIcon(id, parentDir, icon, fileRefs)
            }
            UserPresetTuple(it, p, derPublicKey, ad, id, algoType)
        }
        return users to fileRefs
    }

    private suspend fun getPubKeyAndAddress(
        parentDir: File,
        privatePath: String,
        algoType: AlgoType
    ): Pair<String, String> {
        return getAlgo(algoType).run {
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
        tuples.forEach { topicTuple ->
            uploadTopicMedias(parentDir, userMap, topicTuple.id, topicTuple.topic)
        }
        // 为 topic 作者添加 user log
        tuples.forEach { tuple ->
            val authorId = userMap[tuple.topic.author]!!.id
            addUserLog(authorId, UserLogType.CREATE, tuple.id ob ObjectType.TOPIC).getOrThrow()
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
        val fileInfos = uploadFile(author, ObjectType.USER, parentDir, mediaLink.map {
            "medias/topics/$it"
        })
        val fileRefs = fileInfos.map { info ->
            FileRef(
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                objectId = topicId,
                objectType = ObjectType.TOPIC,
                author = author,
                mediaName = info.name,
                fileId = info.id,
            )
        }
        database.file.insertFileRefs(fileRefs).getOrThrow()
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
        val roomMembers = database.admin.getAllMembers(roomAids).getOrThrow().groupBy {
            it.third
        }
        val encryptedContents = topicList.map {
            val (encryptedContent, aesBytes) = encryptDataByAES(
                getTopicContent(it, parentDir)
            ).getOrThrow()
            EncryptedTopicTuple(encryptedContent, aesBytes, SnowflakeFactory.nextId(), it)
        }
        val encryptedKeys = encryptedContents.flatMap {
            val topic = it.presetTopic
            val id = it.id
            val aesBytes = it.aesKey
            roomMembers[topic.room]!!.map { (derPublicKey, uid) ->
                Triple(id, getAlgo().encryptionAlgo.kemEncrypt(derPublicKey, aesBytes).getOrThrow(), uid)
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
        // 为 topic 作者添加 user log
        tuples.forEach { tuple ->
            val authorId = userMap[tuple.topic.author]!!.id
            addUserLog(authorId, UserLogType.CREATE, tuple.id ob ObjectType.TOPIC).getOrThrow()
        }
        batchAddSubscriptions(tuples, userMap)
    }

    private suspend fun Backend.batchAddSubscriptions(
        tuples: List<InsertTopicTuple>,
        userMap: Map<String, User>
    ) {
        database.admin.batchAddSubscription(tuples.map {
            UserSubscription(it.id, userMap[it.topic.author]!!.id, it.id, ObjectType.TOPIC, now())
        }).getOrThrow()
        // 为订阅添加 user log
        tuples.forEach { tuple ->
            val authorId = userMap[tuple.topic.author]!!.id
            addUserLog(authorId, UserLogType.ADD_SUBSCRIPTION, tuple.id ob ObjectType.TOPIC).getOrThrow()
        }
    }

    suspend fun Backend.uploadFile(
        id: PrimaryKey,
        type: ObjectType,
        parentDir: File,
        p: List<String>
    ): List<FileInfo> {
        if (p.isEmpty()) return emptyList()
        return tryUploadFiles(
            id,
            type,
            p.map {
                val path = File(parentDir, it)
                val name = path.name
                UploadPack(path, name, path.length(), "$id/$name")
            }
        ).getOrThrow()
    }

    private fun getTopicContent(presetTopic: PresetTopic, parentDir: File): String {
        val content = if (presetTopic.type == "file") {
            File(parentDir, presetTopic.content).readText().replace("\r\n", "\n")
        } else {
            presetTopic.content
        }
        return content
    }

    private suspend fun Backend.uploadUserIcon(
        id: PrimaryKey,
        parentDir: File,
        icon: String,
        fileRefs: MutableList<Pair<FileInfo, PrimaryKey>>
    ): PrimaryKey {
        val uploadFile = uploadFile(id, ObjectType.USER, parentDir, listOf(icon)).first()
        fileRefs.add(uploadFile to id)
        return uploadFile.id
    }

    private suspend fun Backend.uploadRoomIcon(
        id: PrimaryKey,
        parentDir: File,
        icon: String,
        fileRefs: MutableList<Triple<FileInfo, String, PrimaryKey>>,
        room: PresetRoom
    ): PrimaryKey {
        val fileInfo = uploadFile(id, ObjectType.ROOM, parentDir, listOf(icon)).first()
        fileRefs.add(Triple(fileInfo, room.admin, id))
        return fileInfo.id
    }

    private suspend fun Backend.getUserFromTuple(tuple: UserPresetTuple, parentDir: File): User {
        val encPubKey = if (tuple.algoType == AlgoType.DILITHIUM) {
            val algo = getAlgo(tuple.algoType).encryptionAlgo as Type2Algo
            val encryptionPrivateKey = tuple.presetUser.encryptionPrivateKey
            val pemPrivateKey = if (encryptionPrivateKey != null) {
                File(parentDir, encryptionPrivateKey).readText().replace("\r\n", "\n")
            } else {
                algo.generateEncryptionPemKeyPair().getOrThrow().first
            }
            algo.getDerEncryptionPublicKeyFromPemPrivateKey(pemPrivateKey).getOrThrow()
        } else {
            null
        }
        val notificationId = SnowflakeFactory.nextId()
        return User(
            tuple.presetUser.aid,
            encPubKey,
            tuple.publicKey,
            tuple.address,
            tuple.pic,
            tuple.presetUser.name.takeIf { s -> s.isNotBlank() } ?: nameService.parse(tuple.id),
            tuple.id,
            now(),
            0,
            PassType.RAW,
            tuple.algoType,
            notificationId
        )
    }
}

suspend fun Backend.applyPreset(presetValue: PresetValue, parentDir: File) {
    AddPreset().run {
        applyPresetByValue(presetValue, parentDir)
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
    Napier.i {
        "download status $status"
    }

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

internal fun isDownloadConfigPath(path: String): Boolean {
    return path.endsWith("download") || path.endsWith(".download.yaml") || path.endsWith(".download.yml")
}

internal fun parseDownloadConfig(path: File): DownloadConfig? {
    val text = path.readText()
    return runCatching {
        yamlMapper.readValue(text, DownloadConfig::class.java)
    }.getOrElse {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size >= 3) {
            DownloadConfig(name = lines[0], link = lines[1], hash = lines[2])
        } else {
            null
        }
    }
}

internal suspend fun downloadPresetFileIfNeed(
    path: String,
    parentDir: File?,
    client: HttpClient
): File? = if (isDownloadConfigPath(path)) {
    val configFile = File(parentDir, path)
    val config = parseDownloadConfig(configFile)
    if (config != null) {
        val realPath = File(parentDir, "download/${config.name}")
        if (realPath.exists()) {
            if (config.hash.startsWith("sha256:")) {
                val calculatedSha = sha256File(realPath)
                val hashValue = config.hash.removePrefix("sha256:")
                Napier.i {
                    "calculated ${config.name} sha $calculatedSha, real $hashValue"
                }
                if (calculatedSha != hashValue) {
                    downloadWithResume(config.link, realPath, client)
                }
            }
        } else {
            downloadWithResume(config.link, realPath, client)
        }

        repackArchiveWithExclusionsAndInclusionsInPlace(
            realPath,
            config.excludeArchiveEntries,
            config.includeArchiveEntries
        )
    } else {
        null
    }
} else {
    File(parentDir, path)
}

private fun shouldExcludeEntry(relativePath: String, excludeGlobs: List<String>): Boolean {
    if (excludeGlobs.isEmpty()) return false
    val normalized = relativePath.replace('\\', '/')
    return excludeGlobs.any { pattern ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:${pattern.trim()}")
        matcher.matches(Paths.get(normalized))
    }
}

private fun shouldIncludeEntry(relativePath: String, includeGlobs: List<String>): Boolean {
    if (includeGlobs.isEmpty()) return true
    val normalized = relativePath.replace('\\', '/')
    return includeGlobs.any { pattern ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:${pattern.trim()}")
        matcher.matches(Paths.get(normalized))
    }
}

private fun unzipToDirectory(zipFile: File, outputDir: File) {
    ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            val target = File(outputDir, entry.name)
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                BufferedOutputStream(target.outputStream()).use { output ->
                    zis.copyTo(output)
                }
            }
            zis.closeEntry()
        }
    }
}

private fun zipDirectoryWithFilter(
    sourceDir: File,
    targetZip: File,
    excludeGlobs: List<String>,
    includeGlobs: List<String>
) {
    ZipOutputStream(BufferedOutputStream(targetZip.outputStream())).use { zos ->
        sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = sourceDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            if (shouldExcludeEntry(relative, excludeGlobs)) {
                return@forEach
            }
            if (!shouldIncludeEntry(relative, includeGlobs)) {
                return@forEach
            }
            zos.putNextEntry(ZipEntry(relative))
            BufferedInputStream(file.inputStream()).use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }
}

internal fun repackArchiveWithExclusionsAndInclusionsInPlace(
    zipFile: File,
    excludeGlobs: List<String>,
    includeGlobs: List<String>
): File {
    if ((excludeGlobs.isEmpty() && includeGlobs.isEmpty()) || !zipFile.name.endsWith(".zip", ignoreCase = true)) {
        return zipFile
    }
    val parent = zipFile.parentFile ?: return zipFile
    val extractDir = File(parent, ".${zipFile.name}.extract-${System.currentTimeMillis()}")
    val tempZip = File(parent, ".${zipFile.name}.filtered.tmp")
    try {
        unzipToDirectory(zipFile, extractDir)
        zipDirectoryWithFilter(extractDir, tempZip, excludeGlobs, includeGlobs)
        check(zipFile.delete()) {
            "failed to delete original archive ${zipFile.absolutePath}"
        }
        check(tempZip.renameTo(zipFile)) {
            "failed to replace archive ${zipFile.absolutePath}"
        }
        return zipFile
    } finally {
        extractDir.deleteRecursively()
        if (tempZip.exists()) {
            tempZip.delete()
        }
    }
}

fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    BufferedInputStream(file.inputStream()).use { fis ->
        val buffer = ByteArray(8192)
        var read = fis.read(buffer)
        while (read != -1) {
            digest.update(buffer, 0, read)
            read = fis.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
