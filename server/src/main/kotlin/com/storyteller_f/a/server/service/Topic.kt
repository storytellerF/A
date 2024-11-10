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
import com.storyteller_f.shared.type.Tuple5
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.tables.checkRoomIsPrivate
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
    return checkRootWritePermission(newTopic.parentType, newTopic.parentId, uid)?.let { (t, p, hasWrite) ->
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
            val info = DatabaseFactory.dbQuery {
                val newTopicId = Topic.new(topic)
                backend.topicDocumentService.saveDocument(listOf(TopicDocument(newTopicId, content)))
                topic.toTopicInfo()
            }
            return Result.success(info)
        } else {
            Result.failure(ForbiddenException("Permission denied."))
        }
    } ?: Result.success(null)
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
    return runCatching {
        DatabaseFactory.queryNotNull(User::toUserInfo) {
            User.findById(id)
        }?.let { creatorInfo ->
            DatabaseFactory.queryNotNull({
                toTopicInfo()
            }) {
                Topic.findById(topicId)
            }?.let {
                checkRootReadPermission(ObjectType.TOPIC, topicId, id)?.let { (_, _, hasRead) ->
                    if (hasRead) {
                        getTopicSnapshot(topicId, it, creatorInfo, backend)
                    } else {
                        null
                    }
                }
            }
        }
    }
}

private suspend fun getTopicSnapshot(
    topicId: PrimaryKey,
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
    topicId: PrimaryKey,
    uid: PrimaryKey?,
    backend: Backend
): Result<TopicInfo?> {
    return checkRootReadPermission(ObjectType.TOPIC, topicId, uid)?.let { (_, _, hasRead, hasJoined, isPrivate) ->
        if (hasRead) {
            Result.success(DatabaseFactory.queryNotNull(Topic::toTopicInfo) {
                Topic.findById(topicId)
            }?.let { info ->
                if (isPrivate) {
                    DatabaseFactory.dbQuery { getEncryptedTopicContent(listOf(topicId), uid) }.firstOrNull()
                        ?.let { id -> info.copy(content = id) }
                } else {
                    backend.topicDocumentService.getDocument(listOf(topicId)).firstOrNull()?.content?.let {
                        info.copy(content = TopicContent.Plain(it))
                    }
                }
            }?.copy(hasJoined = hasJoined))
        } else {
            Result.failure(ForbiddenException())
        }
    } ?: Result.success(null)
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
        val topicContents = checkRootReadPermission(parentType, parentId, uid)?.let { (_, _, hasRead, _, isPrivate) ->
            when {
                !isPrivate -> {
                    backend.topicDocumentService.getDocument(data.map {
                        it.id
                    }).mapNotNull {
                        it?.let { it1 -> TopicContent.Plain(it1.content) }
                    }
                }

                hasRead -> {
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
        }
        data.mapIndexed { index, l ->
            topicContents?.get(index)?.let {
                l.copy(content = it)
            } ?: l
        } to count
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

suspend fun checkRootReadPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Tuple5<ObjectType, PrimaryKey, Boolean, Boolean, Boolean>? {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.queryNotNull({
                rootId to rootType
            }) {
                Topic.findById(parentId)
            }?.let { (rootId, rootType) ->
                if (rootType != ObjectType.ROOM) {
                    Tuple5(rootType, rootId, true, uid?.let {
                        DatabaseFactory.dbQuery {
                            isCommunityJoined(rootId, uid)
                        }
                    } == true, false)
                } else {
                    val hasJoined = uid?.let {
                        DatabaseFactory.dbQuery { isRoomJoined(rootId, uid) }
                    } == true
                    val isPrivate = DatabaseFactory.dbQuery { checkRoomIsPrivate(rootId) }
                    if (isPrivate) {
                        Tuple5(
                            rootType,
                            rootId,
                            hasJoined,
                            hasJoined,
                            true
                        )
                    } else {
                        Tuple5(rootType, rootId, true, hasJoined, false)
                    }
                }
            }
        }

        ObjectType.ROOM -> {
            val hasJoined = uid?.let {
                DatabaseFactory.dbQuery {
                    isRoomJoined(parentId, it)
                }
            } == true
            val isPrivate = DatabaseFactory.dbQuery {
                checkRoomIsPrivate(parentId)
            }
            if (isPrivate) {
                Tuple5(parentType, parentId, hasJoined, hasJoined, true)
            } else {
                Tuple5(parentType, parentId, true, hasJoined, false)
            }
        }

        ObjectType.COMMUNITY -> {
            val hasJoined = uid?.let {
                DatabaseFactory.dbQuery {
                    isCommunityJoined(parentId, it)
                }
            } == true
            Tuple5(parentType, parentId, true, hasJoined, false)
        }

        ObjectType.USER -> TODO()
    }
}

suspend fun checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Triple<ObjectType, PrimaryKey, Boolean>? {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.queryNotNull({
                rootId to rootType
            }) {
                Topic.findById(parentId)
            }?.let { (rootId, rootType) ->
                if (rootType == ObjectType.ROOM) {
                    Triple(rootType, rootId, DatabaseFactory.dbQuery {
                        isRoomJoined(rootId, uid)
                    })
                } else {
                    Triple(rootType, rootId, DatabaseFactory.dbQuery {
                        isCommunityJoined(rootId, uid)
                    })
                }
            }
        }

        ObjectType.ROOM -> {
            Triple(parentType, parentId, DatabaseFactory.dbQuery {
                isRoomJoined(parentId, uid)
            })
        }

        ObjectType.COMMUNITY -> {
            Triple(parentType, parentId, DatabaseFactory.dbQuery {
                isCommunityJoined(parentId, uid)
            })
        }

        ObjectType.USER -> TODO()
    }
}
