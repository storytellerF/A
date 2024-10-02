package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.hmacSign
import com.storyteller_f.shared.hmacVerify
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.TopicSnapshot
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll


suspend fun RoutingContext.addTopicAtCommunity(uid: OKey, backend: Backend): Result<TopicInfo?> {
    val newTopic = call.receive<NewTopic>()
    return when (newTopic.parentType) {

        ObjectType.COMMUNITY -> {
            if (newTopic.content is TopicContent.Encrypted) {
                Result.failure(ForbiddenException("Community only accept unencrypted content."))
            } else {
                addTopicIntoCommunity(
                    newTopic.parentId, uid,
                    (newTopic.content as TopicContent.Plain).plain, newTopic.parentId, ObjectType.COMMUNITY, backend
                )
            }
        }

        ObjectType.TOPIC -> {
            addTopicIntoTopic(newTopic.parentId, uid, (newTopic.content as TopicContent.Plain).plain, backend)
        }

        else -> Result.failure(ForbiddenException("invalid parentType: ${newTopic.parentType}"))
    }
}

suspend fun addTopicIntoTopic(parentTopicId: OKey, uid: OKey, content: String, backend: Backend): Result<TopicInfo?> {
    val topic = DatabaseFactory.queryNotNull({
        rootId to rootType
    }) {
        Topic.findById(parentTopicId)
    }
    return if (topic != null && topic.second == ObjectType.COMMUNITY) {
        addTopicIntoCommunity(topic.first, uid, content, parentTopicId, ObjectType.TOPIC, backend)
    } else {
        Result.failure(ForbiddenException())
    }
}

suspend fun addTopicIntoCommunity(
    communityId: OKey,
    uid: OKey,
    content: String,
    id: OKey,
    type: ObjectType,
    backend: Backend
): Result<TopicInfo?> {
    if (isCommunityJoined(communityId, uid)) {
        val newId = SnowflakeFactory.nextId()
        val topic = Topic(
            author = uid,
            parentId = id,
            parentType = type,
            rootId = communityId,
            rootType = ObjectType.COMMUNITY,
            lastModifiedTime = now(),
            id = newId,
            createdTime = now(),
        )
        val info = DatabaseFactory.dbQuery {
            val newTopicId = Topic.new(topic)
            backend.topicDocumentService.saveDocument(listOf(TopicDocument(newTopicId, content)))
            topic.toTopicInfo()
        }
        return Result.success(info)
    } else {
        return Result.failure(ForbiddenException("Permission denied."))
    }
}

fun Topic.toTopicInfo(): TopicInfo {
    return TopicInfo(
        id = id,
        content = TopicContent.Plain(""),
        author = author,
        rootId = rootId,
        rootType = rootType,
        parentId = parentId,
        parentType = parentType,
        createdTime = createdTime,
        lastModifiedTime = now(),
    )
}


suspend fun getTopicSnapshot(id: OKey, topicId: OKey, backend: Backend): Result<TopicSnapshotPack?> {
    return runCatching {
        DatabaseFactory.queryNotNull(User::toUserInfo) {
            User.findById(id)
        }?.let { creatorInfo ->
            DatabaseFactory.queryNotNull({
                toTopicInfo()
            }) {
                Topic.findById(topicId)
            }?.let {
                val (isPrivate) = isPrivateChat(ObjectType.TOPIC, topicId)
                if (!isPrivate) {
                    getTopicSnapshot(topicId, it, creatorInfo, backend)
                } else {
                    null
                }
            }
        }
    }
}

private suspend fun getTopicSnapshot(
    topicId: OKey,
    it: TopicInfo,
    creatorInfo: UserInfo,
    backend: Backend
): TopicSnapshotPack? {
    return backend.topicDocumentService.getDocument(listOf(topicId)).firstOrNull()?.let { content ->
        DatabaseFactory.queryNotNull(User::toUserInfo) {
            User.findById(it.author)
        }?.let { authorInfo ->
            val snapshot = TopicSnapshot(
                authorAddress = if (authorInfo.aid == null) authorInfo.address else null,
                authorAid = authorInfo.aid,
                content = content.content,
                creatorAddress = if (creatorInfo.aid == null) creatorInfo.address else null,
                creatorAid = creatorInfo.aid,
                topicCreatedTime = it.createdTime,
                topicModifiedTime = it.lastModifiedTime,
                capturedTime = now()
            )
            val hash = calcHash(snapshot, backend)
            TopicSnapshotPack(snapshot, hash)
        }
    }
}

suspend fun calcHash(snapshot: TopicSnapshot, backend: Backend): String {
    val input = getSnapshotInput(snapshot)
    val hmacKey = backend.config.hmacKey
    return hmacSign(hmacKey, input)
}

private fun getSnapshotInput(snapshot: TopicSnapshot): String {
    val input = buildString {
        snapshot.creatorAddress?.let {
            appendLine(it)
        }
        snapshot.creatorAid?.let {
            appendLine(it)
        }
        snapshot.authorAddress?.let {
            appendLine(it)
        }
        snapshot.authorAid?.let {
            appendLine(it)
        }
        appendLine(snapshot.content)
        appendLine(snapshot.topicCreatedTime)
        appendLine(snapshot.topicModifiedTime)
        appendLine(snapshot.capturedTime)
    }
    return input
}


suspend fun getTopic(
    topicId: OKey,
    it: OKey?,
    backend: Backend
): Result<TopicInfo?> {
    val (isPrivateChat, roomId) = isPrivateChat(ObjectType.TOPIC, topicId)
    return if (!isPrivateChat || it != null && isRoomJoined(roomId, it)) {
        Result.success(DatabaseFactory.queryNotNull(Topic::toTopicInfo) {
            Topic.findById(topicId)
        }?.let { info ->
            if (isPrivateChat) {
                info.copy(content = DatabaseFactory.dbQuery { getEncryptedTopicContent(listOf(topicId), it) }.first())
            } else {
                backend.topicDocumentService.getDocument(listOf(topicId)).firstOrNull()?.content?.let {
                    info.copy(content = TopicContent.Plain(it))
                }
            }
        })
    } else {
        Result.failure(ForbiddenException())
    }
}


suspend fun RoutingContext.verifySnapshot(backend: Backend) = runCatching {
    val pack = call.receive<TopicSnapshotPack>()
    val hmacKey = backend.config.hmacKey
    hmacVerify(hmacKey, pack.hash, getSnapshotInput(pack.snapshot))
}


suspend fun getTopics(
    parentId: OKey,
    parentType: ObjectType,
    uid: OKey? = null,
    backend: Backend,
    preTopicId: OKey?,
    nextTopicId: OKey?,
    size: Int
): Result<Pair<List<TopicInfo>, Long>> {
    return runCatching {
        val data = DatabaseFactory.mapQuery(Topic::toTopicInfo, Topic::wrapRow) {
            Topics
                .select(Topics.fields)
                .where {
                    Topics.parentId eq parentId and (Topics.parentType eq parentType)
                }.bindPaginationQuery(Topics, preTopicId, nextTopicId, size)
        }
        val count = DatabaseFactory.count {
            Topics
                .select(Topics.fields)
                .where {
                    Topics.parentId eq parentId and (Topics.parentType eq parentType)
                }
        }
        val (isPrivateChat, roomId) = isPrivateChat(parentType, parentId)
        val topicContents = when {
            !isPrivateChat -> {
                backend.topicDocumentService.getDocument(data.map {
                    it.id
                }).mapNotNull {
                    it?.let { it1 -> TopicContent.Plain(it1.content) }
                }
            }

            uid != null && isRoomJoined(roomId, uid) -> {
                DatabaseFactory.dbQuery {
                    getEncryptedTopicContent(data.map {
                        it.id
                    }, uid)
                }

            }

            else -> {
                return Result.failure(ForbiddenException())
            }
        }

        data.mapIndexed { index, l ->
            topicContents[index].let {
                l.copy(content = it)
            }
        } to count
    }

}

class ForbiddenException(message: String = "Invalid operation") : Exception(message)


@OptIn(ExperimentalStdlibApi::class)
fun getEncryptedTopicContent(topicId: List<OKey>, uid: OKey?): List<TopicContent.Encrypted> {
    val aesMap = EncryptedTopicKeys.selectAll().where {
        val op = EncryptedTopicKeys.topicId inList topicId
        if (uid != null) {
            op and (EncryptedTopicKeys.uid eq uid)
        } else {
            op
        }
    }.map {
        EncryptedTopicKey.wrapRow(it)
    }.groupBy {
        it.topicId
    }.mapValues { listEntry ->
        listEntry.value.map {
            it.uid to it.encryptedAes.toHexString()
        }
    }
    val contentMap = EncryptedTopics.selectAll().where {
        EncryptedTopics.topicId inList topicId
    }.map {
        EncryptedTopic.wrapRow(it)
    }.groupBy {
        it.topicId
    }.mapValues {
        it.value.first().content.toHexString()
    }
    return topicId.map {
        val map = aesMap[it].orEmpty().toMap()
        val content = contentMap[it].orEmpty()
        TopicContent.Encrypted(content, map)
    }
}

suspend fun isPrivateChat(parentType: ObjectType, parentId: OKey): Pair<Boolean, OKey> {
    val b1 = parentType == ObjectType.TOPIC
    return when {
        b1 -> {
            DatabaseFactory.query({
                it?.let {
                    it.rootId to it.rootType
                }
            }) {
                Topic.findById(parentId)
            }?.let { (rootId, rootType) ->
                if (rootType == ObjectType.ROOM && checkRoomIsPrivate(rootId)) {
                    true to rootId
                } else {
                    false to 0u
                }
            }!!
        }

        parentType == ObjectType.ROOM && checkRoomIsPrivate(parentId) -> {
            true to parentId
        }

        else -> {
            false to 0u
        }
    }
}
