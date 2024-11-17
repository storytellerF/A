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
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.tables.checkRoomIsPrivate
import com.storyteller_f.types.PaginationResult
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

suspend fun RoutingContext.addTopicAtCommunity(uid: PrimaryKey, backend: Backend): Result<TopicInfo?> {
    val newTopic = call.receive<NewTopic>()
    if (newTopic.content is TopicContent.Encrypted) {
        return Result.failure(ForbiddenException("Community only accept unencrypted content."))
    }
    val content = (newTopic.content as TopicContent.Plain).plain
    return checkRootWritePermission(newTopic.parentType, newTopic.parentId, uid).mapResultNotNull { (t, p, hasWrite) ->
        if (hasWrite) {
            val newId = SnowflakeFactory.nextId()
            val topic = Topic(
                author = uid,
                parentId = newTopic.parentId,
                parentType = newTopic.parentType,
                rootId = p,
                rootType = t,
                lastModifiedTime = now(),
                id = newId,
                createdTime = now(),
            )
            DatabaseFactory.dbQuery {
                val newTopicId = Topic.new(topic)
                backend.topicDocumentService.saveDocument(listOf(TopicDocument(newTopicId, content)))
                topic.toTopicInfo().copy(content = TopicContent.Plain(content))
            }
        } else {
            Result.failure(ForbiddenException("Permission denied."))
        }
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
        hasJoined = false,
        createdTime = createdTime,
        lastModifiedTime = now(),
    )
}

suspend fun getTopicSnapshot(id: PrimaryKey, topicId: PrimaryKey, backend: Backend): Result<TopicSnapshotPack?> {
    return DatabaseFactory.queryNotNull(User::toUserInfo) {
        User.findById(id)
    }.mapResultNotNull { creatorInfo ->
        checkRootReadPermission(ObjectType.TOPIC, topicId, id).mapResultNotNull { (_, _, hasRead) ->
            if (hasRead) {
                DatabaseFactory.queryNotNull({
                    toTopicInfo()
                }) {
                    Topic.findById(topicId)
                }.mapResultNotNull { value ->
                    getTopicSnapshot(topicId, value, creatorInfo, backend)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied."))
            }
        }
    }
}

private suspend fun getTopicSnapshot(
    topicId: PrimaryKey,
    it: TopicInfo,
    creatorInfo: UserInfo,
    backend: Backend
): Result<TopicSnapshotPack?> {
    return backend.topicDocumentService.getDocument(listOf(topicId)).map { value -> value.firstOrNull() }
        .mapResultNotNull { documents ->
            DatabaseFactory.queryNotNull(User::toUserInfo) {
                User.findById(it.author)
            }.map { value ->
                value?.let { authorInfo ->
                    val snapshot = TopicSnapshot(
                        authorAddress = if (authorInfo.aid == null) authorInfo.address else null,
                        authorAid = authorInfo.aid,
                        content = documents.content,
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
    topicId: PrimaryKey,
    uid: PrimaryKey?,
    backend: Backend
): Result<TopicInfo?> {
    return checkRootReadPermission(
        ObjectType.TOPIC,
        topicId,
        uid
    ).mapResultNotNull { (_, _, hasRead, hasJoined, isPrivate) ->
        if (hasRead) {
            DatabaseFactory.queryNotNull(Topic::toTopicInfo) {
                Topic.findById(topicId)
            }.mapResultNotNull { info ->
                if (isPrivate) {
                    DatabaseFactory.dbQuery { getEncryptedTopicContent(listOf(topicId), uid) }.map { value ->
                        value.firstOrNull()?.let { id -> info.copy(content = id) }
                    }
                } else {
                    backend.topicDocumentService.getDocument(listOf(topicId)).map { value ->
                        value.firstOrNull()?.content?.let {
                            info.copy(content = TopicContent.Plain(it))
                        }
                    }
                }
            }.mapNotNull { value ->
                value.copy(hasJoined = hasJoined)
            }
        } else {
            Result.failure(ForbiddenException())
        }
    }
}

suspend fun RoutingContext.verifySnapshot(backend: Backend) = runCatching {
    val pack = call.receive<TopicSnapshotPack>()
    val hmacKey = backend.config.hmacKey
    hmacVerify(hmacKey, pack.hash, getSnapshotInput(pack.snapshot))
}

suspend fun getTopics(
    parentId: PrimaryKey,
    parentType: ObjectType,
    uid: PrimaryKey? = null,
    backend: Backend,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int
): Result<PaginationResult<TopicInfo>?> {
    val baseQuery = Topics
        .select(Topics.fields)
        .where {
            Topics.parentId eq parentId and (Topics.parentType eq parentType)
        }
    return DatabaseFactory.mapQuery(Topic::toTopicInfo, Topic::wrapRow) {
        baseQuery.bindPaginationQuery(Topics, preTopicId, nextTopicId, size)
    }.mapResult { data ->
        DatabaseFactory.count {
            baseQuery
        }.mapResult { count ->
            checkRootReadPermission(parentType, parentId, uid).mapResultNotNull { (_, _, hasRead, _, isPrivate) ->
                when {
                    !isPrivate -> backend.topicDocumentService.getDocument(data.map {
                        it.id
                    }).map {
                        it.mapNotNull {
                            it?.let { it1 -> TopicContent.Plain(it1.content) }
                        }
                    }

                    hasRead -> DatabaseFactory.dbQuery {
                        getEncryptedTopicContent(data.map {
                            it.id
                        }, uid)
                    }

                    else -> Result.failure(ForbiddenException())
                }
            }.map { topicContents ->
                PaginationResult(data.mapIndexed { index, l ->
                    topicContents?.get(index)?.let {
                        l.copy(content = it)
                    } ?: l
                }, count)
            }
        }
    }
}

class ForbiddenException(message: String = "Invalid operation") : Exception(message)

@OptIn(ExperimentalStdlibApi::class)
fun getEncryptedTopicContent(topicId: List<PrimaryKey>, uid: PrimaryKey?): List<TopicContent.Encrypted> {
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

data class RootReadPermission(
    val type: ObjectType,
    val id: PrimaryKey,
    val hasRead: Boolean,
    val hasJoined: Boolean,
    val isPrivate: Boolean
)

suspend fun checkRootReadPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.queryNotNull({
                rootId to rootType
            }) {
                Topic.findById(parentId)
            }.mapResultNotNull { (rootId, rootType) ->
                checkRootReadPermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            isRoomJoined(parentId, uid).mapResult { hasJoined ->
                checkRoomIsPrivate(parentId).map { isPrivate ->
                    RootReadPermission(parentType, parentId, hasJoined, hasJoined, isPrivate)
                }
            }
        }

        ObjectType.COMMUNITY -> {
            isCommunityJoined(parentId, uid).map { hasJoined ->
                RootReadPermission(parentType, parentId, true, hasJoined, false)
            }
        }

        ObjectType.USER -> TODO()
    }
}

suspend fun checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<Triple<ObjectType, PrimaryKey, Boolean>?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.queryNotNull({
                rootId to rootType
            }) {
                Topic.findById(parentId)
            }.mapResultNotNull { (rootId, rootType) ->
                checkRootWritePermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            isRoomJoined(parentId, uid).map { hasJoined ->
                Triple(parentType, parentId, hasJoined)
            }
        }

        ObjectType.COMMUNITY -> {
            isCommunityJoined(parentId, uid).map { hasJoined ->
                Triple(parentType, parentId, hasJoined)
            }
        }

        ObjectType.USER -> TODO()
    }
}

suspend fun searchTopics(
    nextTopicId: PrimaryKey?,
    size: Int,
    word: List<String>,
    backend: Backend
): Result<PaginationResult<TopicInfo>?> {
    return backend.topicDocumentService.searchDocument(word, size, nextTopicId).mapResult { documents ->
        val map = documents.list.groupBy {
            it.id
        }
        val ids = documents.list.map {
            it.id
        }
        DatabaseFactory.mapQuery(Topic::toTopicInfo, Topic::wrapRow) {
            Topics.select(Topics.fields).where {
                Topics.id inList ids
            }
        }.map { infos ->
            infos.mapNotNull { t ->
                map[t.id]?.let {
                    t.copy(content = TopicContent.Plain(it.first().content))
                }
            }
        }.map { value ->
            PaginationResult(value, documents.total)
        }
    }
}
