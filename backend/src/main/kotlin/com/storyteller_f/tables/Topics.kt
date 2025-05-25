package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UnauthorizedException
import com.storyteller_f.shared.utils.*
import com.storyteller_f.tables.Topic.Companion.wrapRow
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

object Topics : BaseTable() {
    val author = customPrimaryKey("author").index()
    val parentId = customPrimaryKey("parent_id").index()
    val parentType = objectType("parent_type")
    val rootId = customPrimaryKey("root_id").index()
    val rootType = objectType("root_type")
    val pinned = bool("pinned").default(false)
    val lastModifiedTime = datetime("last_modified_time").nullable()
}

class Topic(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val author: PrimaryKey,
    val parentId: PrimaryKey,
    val parentType: ObjectType,
    val rootId: PrimaryKey,
    val rootType: ObjectType,
    val isPin: Boolean = false,
    val lastModifiedTime: LocalDateTime? = null,
    val aid: String? = null,
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Topic {
            return with(Topics) {
                Topic(
                    row[id],
                    row[createdTime],
                    row[author],
                    row[parentId],
                    row[parentType],
                    row[rootId],
                    row[rootType],
                    row[pinned],
                    row[lastModifiedTime],
                    row.getOrNull(Aids.value),
                )
            }
        }

        fun findById(topicId: PrimaryKey) = Topics.selectAll().where {
            Topics.id eq topicId
        }

        fun new(info: Topic) {
            return check(Topics.insert {
                it[id] = info.id
                it[author] = info.author
                it[createdTime] = now()
                it[parentType] = info.parentType
                it[parentId] = info.parentId
                it[rootId] = info.rootId
                it[rootType] = info.rootType
            }.insertedCount > 0) {
                "insert topic failed"
            }
        }
    }
}

fun Topic.toTopicInfo(
    commentCount: Long = 0,
    hasComment: Boolean = false,
    reactionCount: Long = 0,
    aidValue: String? = null,
    lastRead: PrimaryKey? = null,
    isPrivate: Boolean = false,
): TopicInfo {
    return TopicInfo(
        id = id,
        content = TopicContent.Nil,
        author = author,
        rootId = rootId,
        rootType = rootType,
        parentId = parentId,
        parentType = parentType,
        hasJoined = false,
        createdTime = createdTime,
        commentCount = commentCount,
        reactionCount = reactionCount,
        hasComment = hasComment,
        isPrivate = isPrivate,
        isPin = isPin,
        lastModifiedTime = lastModifiedTime,
        extension = null,
        aid = aidValue ?: aid,
        lastRead = lastRead,
    )
}

suspend fun DatabaseFactory.getTopicRoot(
    backend: Backend,
    parentId: PrimaryKey
): Result<ObjectTuple?> = dbSearch(backend) {
    search {
        Topic.findById(parentId)
    }
    first {
        val topic = wrapRow(it)
        ObjectTuple(
            topic.rootId,
            topic.rootType,
        )
    }
}

/**
 * 用于生成snapshot
 */
suspend fun DatabaseFactory.getDirectTopic(
    backend: Backend,
    topicId: PrimaryKey
): Result<TopicInfo?> = dbSearch(backend) {
    search {
        Topic.findById(topicId)
    }
    first {
        wrapRow(it).toTopicInfo()
    }
}

suspend fun DatabaseFactory.getTopicCommentCount(topicId: List<PrimaryKey>, backend: Backend) =
    dbSearch(backend) {
        val countColumn = Topics.id.countDistinct()
        search {
            Topics.select(countColumn, Topics.id).where {
                Topics.parentId inList topicId
            }.groupBy(Topics.id)
        }
        map {
            it[Topics.id] to it[countColumn]
        }
    }

suspend fun DatabaseFactory.isUserCommented(
    backend: Backend,
    uid: PrimaryKey,
    topicId: List<PrimaryKey>
) = dbSearch(backend) {
    search {
        Topics.select(Topics.parentId).where {
            Topics.parentId inList topicId and (Topics.author eq uid)
        }
    }
    map {
        it[Topics.parentId]
    }
}

suspend fun DatabaseFactory.getTopicInfo(
    backend: Backend,
    fetch: ObjectFetch,
    uid: PrimaryKey?
): Result<TopicInfo?> {
    return dbSearch(backend) {
        search {
            Topics.join(Aids, JoinType.LEFT, Topics.id, Aids.objectId).select(Topics.fields + Aids.value).where {
                when (fetch) {
                    is ObjectFetch.IdFetch -> Topics.id eq fetch.id
                    is ObjectFetch.AidFetch -> Aids.value eq fetch.aid
                }
            }
        }
        first(Topic::wrapRow)
    }.mapResultIfNotNull { topic ->
        processTopicInfo(uid, listOf(topic), backend).map {
            it.first()
        }
    }
}

private suspend fun processTopicInfo(
    uid: PrimaryKey?,
    topics: List<Topic>,
    backend: Backend
): Result<List<TopicInfo>> = (if (uid == null) {
    Result.success(Merged4(emptySet(), emptyMap(), emptyMap(), emptyMap()))
} else {
    val topicIds = topics.map {
        it.id
    }
    merge({
        DatabaseFactory.isUserCommented(backend, uid, topicIds).map {
            it.toSet()
        }
    }, {
        DatabaseFactory.getTopicCommentCount(topicIds, backend).map {
            it.associateByPair()
        }
    }, {
        DatabaseFactory.getReactionCount(backend, topicIds).map {
            it.associateByPair()
        }
    }, {
        DatabaseFactory.getReadLogs(backend, topicIds, uid).map {
            it.associateBy { userTopicRead ->
                userTopicRead.objectId
            }
        }
    })
}).map { (commented, commentCountMap, reactionCountMap, lastReadMap) ->
    topics.map { topic ->
        val id = topic.id
        topic.toTopicInfo(
            commentCountMap[id] ?: 0,
            commented.contains(id),
            reactionCountMap[id] ?: 0,
            lastRead = lastReadMap[id]?.topicId
        )
    }
}

/**
 * 根据指定条件获取未填充content 的topic 列表
 */
suspend fun getTopicsByPredicate(
    backend: Backend,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    addPinOrder: Boolean = false,
    addPagingQuery: Query.() -> Query = { this },
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<List<TopicInfo>> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return DatabaseFactory.dbSearch(backend) {
        search {
            Topics.join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
                .select(Topics.fields + Aids.value)
                .where(predicate)
                .let(addPagingQuery)
                .orderBy(
                    *(if (addPinOrder) {
                        arrayOf(
                            Topics.pinned to SortOrder.DESC,
                            Topics.id to SortOrder.DESC
                        )
                    } else {
                        arrayOf(Topics.id to SortOrder.DESC)
                    })
                )
        }
        map(Topic::wrapRow)
    }.mapResult {
        processTopicInfo(uid, it, backend)
    }
}

suspend fun getTopicsPagingByPredicate(
    backend: Backend,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    pagingFetch: PagingFetch,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<PaginationResult<TopicInfo>> {
    return getTopicsByPredicate(backend, uid, fillHasCommented, addPagingQuery = {
        bindPaginationQuery(Topics, pagingFetch)
    }, predicate = predicate).mapResult { data ->
        DatabaseFactory.count(backend) {
            Topics
                .selectAll()
                .where(predicate)
        }.map { count ->
            PaginationResult(data, count)
        }
    }
}

// 加密内容不能处理media，需要客户端处理
@OptIn(ExperimentalStdlibApi::class)
suspend fun DatabaseFactory.getEncryptedTopicContents(
    backend: Backend,
    data: List<TopicInfo>,
    uid: PrimaryKey
) = dbQuery(backend) {
    val topicId = data.map {
        it.id
    }
    val aesMap = EncryptedTopicKeys.selectAll().where {
        EncryptedTopicKeys.topicId inList topicId and (EncryptedTopicKeys.uid eq uid)
    }.map {
        EncryptedTopicKey.wrapRow(it)
    }.associate {
        it.topicId to mapOf((it.uid to it.encryptedAes.toHexString()))
    }
    val contentMap = EncryptedTopics.selectAll().where {
        EncryptedTopics.topicId inList topicId
    }.map {
        EncryptedTopic.wrapRow(it)
    }.associate {
        it.topicId to it.content.toHexString()
    }
    topicId.map {
        val map = aesMap[it] ?: emptyMap()
        val content = contentMap[it].orEmpty()
        TopicContent.Encrypted(content, map)
    }
}

suspend fun DatabaseFactory.savePlainTopic(
    backend: Backend,
    topic: Topic,
    content: TopicContent.Plain
) = dbQuery(backend) {
    Topic.new(topic)
    insertMediaRefs(backend, topic.id, ObjectType.TOPIC, extractMarkdownMediaLink(content.plain).map {
        topic.author to it
    }).getOrThrow()

    backend.topicSearchService.saveDocument(
        listOf(TopicDocument.fromTopic(topic, content))
    ).getOrThrow()
    topic.toTopicInfo().copy(content = content)
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun DatabaseFactory.saveEncryptedTopic(
    backend: Backend,
    topic: Topic,
    content: TopicContent.Encrypted,
) = dbQuery(backend) {
    Topic.new(topic)
    EncryptedTopics.insert {
        it[this.content] = ExposedBlob(content.encrypted.hexToByteArray())
        it[topicId] = topic.id
    }
    EncryptedTopicKeys.batchInsert(content.encryptedKey.keys) {
        this[EncryptedTopicKeys.topicId] = topic.id
        this[EncryptedTopicKeys.uid] = it
        this[EncryptedTopicKeys.encryptedAes] =
            ExposedBlob(content.encryptedKey[it]!!.hexToByteArray())
    }
    topic.toTopicInfo(hasComment = false, isPrivate = true)
        .copy(content = TopicContent.Encrypted(content.encrypted, content.encryptedKey), isPrivate = true)
}

suspend fun DatabaseFactory.updateTopicStatus(
    backend: Backend,
    topicId: PrimaryKey,
    newValue: Boolean
): Result<Boolean> {
    return dbQuery(backend) {
        Topics.update({
            Topics.id eq topicId
        }) {
            it[pinned] = newValue
        } > 0
    }
}

suspend fun DatabaseFactory.getRawTopics(backend: Backend, firstId: PrimaryKey): Result<List<Topic>> {
    return dbSearch(backend) {
        search {
            val query = Topics.selectAll()
            if (firstId != DEFAULT_PRIMARY_KEY) {
                query.andWhere {
                    Topics.id less firstId
                }
            }
            query.orderBy(Topics.id, SortOrder.ASC)
        }
        map(Topic::wrapRow)
    }
}
