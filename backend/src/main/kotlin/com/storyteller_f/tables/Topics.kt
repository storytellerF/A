package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.Tuple4
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Topic.Companion.wrapRow
import com.storyteller_f.types.PaginationResult
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
    val lastModifiedTime: LocalDateTime?
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Topic {
            return Topic(
                row[Topics.id],
                row[Topics.createdTime],
                row[Topics.author],
                row[Topics.parentId],
                row[Topics.parentType],
                row[Topics.rootId],
                row[Topics.rootType],
                row[Topics.lastModifiedTime]
            )
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

suspend fun DatabaseFactory.getTopicRoot(parentId: PrimaryKey): Result<Pair<PrimaryKey, ObjectType>?> = first({
    rootId to rootType
}, Topic::wrapRow) {
    Topic.findById(parentId)
}

suspend fun DatabaseFactory.getSimpleTopic(topicId: PrimaryKey): Result<TopicInfo?> = first({
    toTopicInfo(0, false)
}, Topic::wrapRow) {
    Topic.findById(topicId)
}

fun Topic.toTopicInfo(commentCount: Long = 0, hasComment: Boolean = false, reactionCount: Long = 0): TopicInfo {
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
        isPrivate = false,
        lastModifiedTime = now(),
        extension = null
    )
}

suspend fun DatabaseFactory.getTopicInfo(topicId: PrimaryKey, uid: PrimaryKey?): Result<TopicInfo?> {
    val t2 = Topics.alias("t2")
    val commentCount = t2[Topics.id].countDistinct()
    val reactionComment = Reactions.id.countDistinct()
    val containExpression = topicAuthorContains(uid, t2)

    val baseSelection = Topics.fields + commentCount + reactionComment
    return first({
        data1.toTopicInfo(data2, data3, data4)
    }, {
        Tuple4(
            wrapRow(it),
            it[commentCount],
            if (containExpression != null) it[containExpression] == 1L else false,
            it[reactionComment]
        )
    }) {
        Topics.join(t2, JoinType.LEFT, Topics.id, t2[Topics.parentId])
            .join(Reactions, JoinType.LEFT, Topics.id, Reactions.objectId)
            .let { join ->
                when {
                    containExpression != null -> join.select(baseSelection + containExpression)
                    else -> join.select(baseSelection)
                }
            }
            .where {
                Topics.id eq topicId
            }
            .groupBy(Topics.id)
    }
}

private fun topicAuthorContains(
    uid: PrimaryKey?,
    t2: Alias<Topics>
): Max<Long, Long>? {
    val containExpression = if (uid != null) {
        Expression.build {
            val expr = case().When(t2[Topics.author].eq(uid), longLiteral(1)).Else(longLiteral(0))
            Max<Long, Long>(expr, LongColumnType())
        }
    } else {
        null
    }
    return containExpression
}

suspend fun getTopicsPagingByPredicate(
    uid: PrimaryKey?,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    fillHasCommented: Boolean?,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<PaginationResult<TopicInfo>> {
    return getTopicsByPredicate(uid, fillHasCommented, {
        it.bindPaginationQuery(Topics, preTopicId, nextTopicId, size)
    }, predicate).mapResult { data ->
        DatabaseFactory.count {
            Topics
                .selectAll()
                .where(predicate)
        }.map { count ->
            PaginationResult(data, count)
        }
    }
}

class TopicSearchTuple(val topicInfo: Topic, val commentCount: Long, val hasComment: Boolean, val reactionCount: Long)

/**
 * 根据指定条件获取未填充content 的topic 列表
 */
suspend fun getTopicsByPredicate(
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    extraPredicate: (Query) -> Query = { it },
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<List<TopicInfo>> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    val t2 = Topics.alias("t2")
    val reactionComment = Reactions.id.countDistinct()
    val containExpression = topicAuthorContains(uid, t2)
    val commentCountColumn = t2[Topics.id].countDistinct()
    val baseSelection = Topics.fields + commentCountColumn + reactionComment
    return DatabaseFactory.mapQuery({
        topicInfo.toTopicInfo(commentCount, hasComment, reactionCount)
    }, {
        TopicSearchTuple(
            wrapRow(it),
            it[commentCountColumn],
            if (containExpression != null) (it[containExpression] == 1L) else false,
            it[reactionComment]
        )
    }) {
        Topics.join(t2, JoinType.LEFT, Topics.id, t2[Topics.parentId])
            .join(Reactions, JoinType.LEFT, Topics.id, Reactions.objectId)
            .let {
                when {
                    containExpression != null -> it.select(baseSelection + containExpression)
                    else -> it.select(baseSelection)
                }
            }
            .where(predicate).let(extraPredicate).groupBy(Topics.id)
    }
}

// 加密内容不能处理media，需要客户端处理
suspend fun DatabaseFactory.getEncryptedTopic(
    data: List<TopicInfo>,
    uid: PrimaryKey
) = dbQuery {
    getEncryptedTopicContent(data.map {
        it.id
    }, uid)
}

@OptIn(ExperimentalStdlibApi::class)
fun getEncryptedTopicContent(topicId: List<PrimaryKey>, uid: PrimaryKey): List<TopicContent.Encrypted> {
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
    return topicId.map {
        val map = aesMap[it] ?: emptyMap()
        val content = contentMap[it].orEmpty()
        TopicContent.Encrypted(content, map)
    }
}

suspend fun DatabaseFactory.getTopicRoot(newTopic: NewRoomTopic) = first({
    rootId to rootType
}, Topic::wrapRow) {
    Topic.findById(newTopic.parentId)
}

suspend fun DatabaseFactory.saveTopic(
    topic: Topic,
    backend: Backend,
    content: TopicContent.Plain
) = dbQuery {
    Topic.new(topic)
    backend.topicSearchService.saveDocument(
        listOf(TopicDocument.fromTopic(topic, content))
    ).getOrThrow()

    topic.toTopicInfo().copy(content = content)
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun DatabaseFactory.saveEncryptedTopic(
    topic: Topic,
    encryptedContent: String,
    encryptedAes: Map<PrimaryKey, String>
) = dbQuery {
    Topic.new(topic)
    EncryptedTopics.insert {
        it[content] = ExposedBlob(encryptedContent.hexToByteArray())
        it[topicId] = topic.id
    }
    EncryptedTopicKeys.batchInsert(encryptedAes.keys) {
        this[EncryptedTopicKeys.topicId] = topic.id
        this[EncryptedTopicKeys.uid] = it
        this[EncryptedTopicKeys.encryptedAes] =
            ExposedBlob(encryptedAes[it]!!.hexToByteArray())
    }
    topic.toTopicInfo(hasComment = false)
        .copy(content = TopicContent.Encrypted(encryptedContent, encryptedAes), isPrivate = true)
}
