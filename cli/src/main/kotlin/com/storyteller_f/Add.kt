package com.storyteller_f

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.AddRoom
import com.storyteller_f.shared.obj.AddTaskValue
import com.storyteller_f.shared.obj.AddTopic
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.Tuple4
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCli::class)
class Add : Subcommand("add", "add entry") {
    private val jsonFilePath by argument(ArgType.String, "json", "json data file path")

    override fun execute() {
        if (!checkInputPath(jsonFilePath)) return
        addProvider()
        DatabaseFactory.init(backend.config.databaseConnection)
        Napier.i {
            "database init done."
        }
        val jsonFile = File(jsonFilePath)

        val addTaskValue =
            ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<AddTaskValue>(jsonFile.readText())
        val parentDir = jsonFile.parentFile
        runBlocking {
            try {
                when (addTaskValue.type) {
                    "community" -> addCommunity(addTaskValue, parentDir)
                    "user" -> addUsers(addTaskValue, parentDir)
                    "topic" -> addTopics(addTaskValue, parentDir)
                    "room" -> addRooms(addTaskValue, parentDir)
                }
                Napier.i {
                    "add done."
                }
            } catch (e: Exception) {
                Napier.i(e) {
                    "exception when add"
                }
            }
        }
    }

    private fun checkInputPath(jsonFilePath: String): Boolean {
        return if (!File(jsonFilePath).exists()) {
            Napier.e {
                "json not found"
            }
            false
        } else {
            true
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun addRooms(addTaskValue: AddTaskValue, parentDir: File?) {
        val l = addTaskValue.roomData ?: return
        val data = l.map {
            val icon = it.icon
            if (icon == null) {
                it to null
            } else {
                val p = "icon/${Uuid.random()}"
                backend.mediaService.upload("apic", listOf(p to File(parentDir, icon).absolutePath))
                it to p
            }
        }
        DatabaseFactory.dbQuery {
            val list = l.flatMap {
                it.users + it.admin
            }.distinct().map {
                User.wrapRow(findUserByAId(it)!!)
            }.groupBy {
                it.aid
            }
            val idList = Rooms.batchInsert(data) { (it, p) ->
                this[Rooms.aid] = it.id
                this[Rooms.icon] = p
                this[Rooms.name] = it.name
                this[Rooms.creator] = list[it.admin]!!.first().id
                this[Rooms.createdTime] = now()
            }.map {
                it[Rooms.id]
            }
            l.forEachIndexed { index, addRoom ->
                RoomJoins.batchInsert(addRoom.users) {
                    this[RoomJoins.uid] = list[it]!!.first().id
                    this[RoomJoins.roomId] = idList[index]
                    this[RoomJoins.joinTime] = now()
                }
            }
            roomJoinCommunity(l, idList)
        }
    }

    private fun roomJoinCommunity(
        l: List<AddRoom>,
        idList: List<OKey>
    ): List<ResultRow> {
        val communities = l.mapNotNull {
            it.community
        }.distinct().map {
            Community.wrapRow(findCommunityByAId(it)!!)
        }.groupBy {
            it.aid
        }
        return CommunityRooms.batchInsert(
            l.mapIndexedNotNull { index, addRoom ->
                if (addRoom.community != null) {
                    addRoom to index
                } else {
                    null
                }
            }
        ) { (first, second) ->
            this[CommunityRooms.roomId] = idList[second]
            this[CommunityRooms.communityId] = communities[first.community]!!.first().id
        }
    }

    private suspend fun addTopics(addTaskValue: AddTaskValue, parentDir: File) {
        DatabaseFactory.dbQuery {
            val data = addTaskValue.topicData!!
            val userList = data.map {
                it.author
            }.distinct().map {
                User.wrapRow(findUserByAId(it)!!)
            }.groupBy {
                it.aid!!
            }
            data.groupBy {
                it.community != null
            }.forEach { (t, u) ->
                if (t) {
                    addTopicsIntoCommunity(u, userList, parentDir)
                } else {
                    addTopicsIntoRoom(u, userList, parentDir)
                }
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun addTopicsIntoRoom(u: List<AddTopic>, userList: Map<String, List<User>>, parentDir: File) {
        val roomList = u.mapNotNull {
            it.room
        }.distinct().map {
            Room.wrapRow(findRoomByAId(it)!!)
        }.groupBy {
            it.aid
        }
        val ids = insertTopicBaseLevel(u, userList, roomList)
        // 检查聊天室是属于社区的还是私有的
        val roomIsPrivate = roomList.mapValues { (_, value) ->
            checkRoomIsPrivate(value.first().id)
        }
        val topicsPrivate = u.mapIndexedNotNull { i, addTopic ->
            if (roomIsPrivate[addTopic.room] == true) {
                addTopic to i
            } else {
                null
            }
        }

        insertEncryptedTopic(topicsPrivate, parentDir, ids, u)
        insertTopic(u, roomIsPrivate, parentDir, ids)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun insertTopicBaseLevel(
        u: List<AddTopic>,
        userList: Map<String, List<User>>,
        roomList: Map<String, List<Room>>
    ): ULongArray {
        val ids = ULongArray(u.size) {
            0u
        }
        val topLevelTopic = u.mapIndexed { index, addTopic ->
            val level = addTopic.level
            val parent = addTopic.parent
            if (parent == null || parent == 0 || level == null || level == 0) {
                addTopic to index
                Triple(addTopic, index, 0)
            } else {
                Triple(addTopic, index, level)
            }
        }.groupBy {
            it.third
        }
        topLevelTopic.keys.sorted().forEach { level ->
            val list = topLevelTopic[level].orEmpty()
            val subIds = Topics.batchInsert(list) { (first, index, _) ->
                this[Topics.author] = userList[first.author]!!.first().id
                this[Topics.createdTime] = now()
                this[Topics.rootId] = roomList[first.room]!!.first().id
                this[Topics.rootType] = ObjectType.ROOM
                this[Topics.parentId] =
                    if (level == 0) roomList[first.room]!!.first().id else ids[index - first.parent!!]
                this[Topics.parentType] = if (level == 0) ObjectType.ROOM else ObjectType.TOPIC
            }.map {
                it[Topics.id]
            }
            subIds.forEachIndexed { index, l ->
                ids[list[index].second] = l
            }
        }
        return ids
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun insertTopic(
        u: List<AddTopic>,
        roomIsPrivate: Map<String, Boolean>,
        parentDir: File,
        ids: ULongArray
    ) {
        val topicsPublic = u.mapIndexedNotNull { i, addTopic ->
            if (roomIsPrivate[addTopic.room] != true) {
                addTopic to i
            } else {
                null
            }
        }
        backend.topicDocumentService.saveDocument(
            topicsPublic.map { (first, second) ->
                val content = getTopicContent(first, parentDir)
                TopicDocument(ids[second], content)
            }
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun insertEncryptedTopic(
        topicsPrivate: List<Pair<AddTopic, Int>>,
        parentDir: File,
        ids: ULongArray,
        u: List<AddTopic>
    ) {
        val rooms = u.mapNotNull {
            it.room
        }.distinct().map { roomId ->
            roomId to RoomJoins
                .join(Rooms, JoinType.INNER, RoomJoins.roomId, Rooms.id)
                .join(Users, JoinType.INNER, RoomJoins.uid, Users.id)
                .select(Users.fields)
                .where {
                    Rooms.aid eq roomId
                }.map {
                    User.wrapRow(it)
                }.map {
                    it.publicKey to it.id
                }
        }.groupBy {
            it.first
        }
        val encrypted = topicsPrivate.map { (addTopic, index) ->
            val (first, aesBytes) = encrypt(getTopicContent(addTopic, parentDir))
            Tuple4(index, first, aesBytes, addTopic)
        }
        val encryptedKeys = encrypted.flatMap { (index, _, aesBytes, topic) ->
            rooms[topic.room]!!.first().second.map {
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

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun addTopicsIntoCommunity(u: List<AddTopic>, userList: Map<String, List<User>>, parentDir: File) {
        val communityList = u.mapNotNull {
            it.community
        }.distinct().map {
            Community.wrapRow(findCommunityByAId(it)!!).let {
                it.id to it.aid
            }
        }.groupBy {
            it.second
        }
        val ids = ULongArray(u.size) {
            0u
        }
        val topLevelTopic = u.mapIndexed { index, addTopic ->
            val level = addTopic.level
            val parent = addTopic.parent
            if (parent == null || parent == 0 || level == null || level == 0) {
                Triple(addTopic, index, 0)
            } else {
                Triple(addTopic, index, level)
            }
        }.groupBy {
            it.third
        }
        topLevelTopic.keys.sorted().forEach { level ->
            val list = topLevelTopic[level].orEmpty()
            val subIds = Topics.batchInsert(list) { (first, index, _) ->
                this[Topics.author] = userList[first.author]!!.first().id
                this[Topics.createdTime] = now()
                this[Topics.rootId] = communityList[first.community]!!.first().first
                this[Topics.rootType] = ObjectType.COMMUNITY
                this[Topics.parentId] =
                    if (level == 0) communityList[first.community]!!.first().first else ids[index - first.parent!!]
                this[Topics.parentType] = if (level == 0) ObjectType.COMMUNITY else ObjectType.TOPIC
            }.map {
                it[Topics.id]
            }
            subIds.forEachIndexed { index, l ->
                ids[list[index].second] = l
            }
        }
        backend.topicDocumentService.saveDocument(
            u.mapIndexedNotNull { index, addTopic ->
                if (addTopic.community != null) {
                    val content = getTopicContent(addTopic, parentDir)
                    TopicDocument(ids[index], content)
                } else {
                    null
                }
            }
        )
    }

    private fun getTopicContent(addTopic: AddTopic, parentDir: File): String {
        val content = if (addTopic.type == "file") {
            File(parentDir, addTopic.content).readText().replace("\r\n", "\n")
        } else {
            addTopic.content
        }
        return content
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun addUsers(addTaskValue: AddTaskValue, parentDir: File?) {
        val l = addTaskValue.userData ?: return
        val data = l.map {
            val derPublicKey =
                getDerPublicKeyFromPrivateKey(File(parentDir, it.privateKey).readText().replace("\r\n", "\n"))
            val ad = calcAddress(derPublicKey)
            val icon = it.icon
            if (icon == null) {
                Tuple4(it, null, derPublicKey, ad)
            } else {
                val p = "icon/${Uuid.random()}"
                val absolutePath = File(parentDir, icon).absolutePath
                backend.mediaService.upload("apic", listOf(p to absolutePath))
                Tuple4(it, null, derPublicKey, ad)
            }
        }
        DatabaseFactory.dbQuery {
            Users.batchInsert(data) { (pair, p, pub, ad) ->
                this[Users.publicKey] = pub
                this[Users.address] = ad
                this[Users.aid] = pair.id
                this[Users.icon] = p
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun addCommunity(addTaskValue: AddTaskValue, parentDir: File?) {
        val data = addTaskValue.communityData!!.map {
            val icon = it.icon
            if (icon == null) {
                it to null
            } else {
                val p = "icon/${Uuid.random()}"
                backend.mediaService.upload("apic", listOf(p to File(parentDir, icon).absolutePath))
                it to p
            }
        }
        DatabaseFactory.dbQuery {
            val systemId = User.wrapRow(
                Users.select(Users.id).where {
                    Users.aid eq "System"
                }.first()
            ).id
            Communities.batchInsert(data) { (it, p) ->
                this[Communities.name] = it.name
                this[Communities.icon] = p
                this[Communities.createdTime] = now()
                this[Communities.owner] = systemId
                this[Communities.aid] = it.id
            }
            data.forEach { (first) ->
                val users = first.users.orEmpty()
                val id = first.id
                val id1 = Community.wrapRow(
                    Communities.select(Communities.id).where {
                        Communities.aid eq id
                    }.first()
                ).id
                userJoinCommunity(users, id1)
            }
        }
    }

    private fun userJoinCommunity(users: List<String>, id1: OKey) {
        users.forEach {
            val value = User.wrapRow(
                Users.select(Users.id).where {
                    Users.aid eq it
                }.first()
            ).id
            createCommunityJoin(value, id1)
        }
    }
}
