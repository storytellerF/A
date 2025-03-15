package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Topic.Companion.wrapRow
import com.storyteller_f.types.PaginationResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

class TopicSearchTuple(
    val topicInfo: Topic,
    val commentCount: Long,
    val hasComment: Boolean,
    val reactionCount: Long,
    val aid: String?
)

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
    val isPin: Boolean,
    val lastModifiedTime: LocalDateTime?,
    val aid: String? = null,
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
                row[Topics.pinned],
                row[Topics.lastModifiedTime],
                row.getOrNull(Aids.value),
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

fun Topic.toTopicInfo(
    commentCount: Long = 0,
    hasComment: Boolean = false,
    reactionCount: Long = 0,
    aidValue: String? = null
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
        isPrivate = false,
        isPin = isPin,
        lastModifiedTime = lastModifiedTime,
        extension = null,
        aid = aidValue ?: aid,
    )
}

suspend fun DatabaseFactory.getTopicRoot(parentId: PrimaryKey): Result<Pair<PrimaryKey, ObjectType>?> = first({
    rootId to rootType
}, Topic::wrapRow) {
    Topic.findById(parentId)
}

/**
 * 用于生成snapshot
 */
suspend fun DatabaseFactory.getSimpleTopic(topicId: PrimaryKey): Result<TopicInfo?> = first({
    toTopicInfo()
}, Topic::wrapRow) {
    Topic.findById(topicId)
}

private fun buildTopicAuthorContainsExpression(
    uid: PrimaryKey?,
    t2: Alias<Topics>
): Max<Long, Long>? {
    val containExpression = if (uid != null) {
        Expression.build {
            val expr = case().When(t2[Topics.author].eq(uid), longLiteral(1)).Else(longLiteral(0))
            Max(expr, LongColumnType())
        }
    } else {
        null
    }
    return containExpression
}

suspend fun DatabaseFactory.getTopicInfo(topicId: PrimaryKey?, aid: String?, uid: PrimaryKey?): Result<TopicInfo?> {
    val t2 = Topics.alias("t2")
    val commentCountColumn = t2[Topics.id].countDistinct()
    val reactionCountColumn = Reactions.id.countDistinct()
    val containColumn = buildTopicAuthorContainsExpression(uid, t2)
    val aidsValue = Aids.value.max()
    val baseSelection = Topics.fields + commentCountColumn + reactionCountColumn + aidsValue
    return first({
        topicInfo.toTopicInfo(commentCount, hasComment, reactionCount, aid)
    }, {
        TopicSearchTuple(
            wrapRow(it),
            it[commentCountColumn],
            if (containColumn != null) it[containColumn] == 1L else false,
            it[reactionCountColumn],
            it[aidsValue]
        )
    }) {
        Topics.join(t2, JoinType.LEFT, Topics.id, t2[Topics.parentId])
            .join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
            .join(Reactions, JoinType.LEFT, Topics.id, Reactions.objectId)
            .let { join ->
                when {
                    containColumn != null -> join.select(baseSelection + containColumn)
                    else -> join.select(baseSelection)
                }
            }
            .where {
                when {
                    topicId != null -> Topics.id eq topicId
                    aid != null -> Aids.value eq aid
                    else -> throw CustomBadRequestException("aid and id is null")
                }
            }
            .groupBy(Topics.id)
    }
}

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
    val reactionCountColumn = Reactions.id.countDistinct()
    val containColumn = buildTopicAuthorContainsExpression(uid, t2)
    val commentCountColumn = t2[Topics.id].countDistinct()
    val aidsValue = Aids.value.max()
    val baseSelection = Topics.fields + commentCountColumn + reactionCountColumn + aidsValue
    return DatabaseFactory.mapQuery({
        topicInfo.toTopicInfo(commentCount, hasComment, reactionCount, aid)
    }, {
        TopicSearchTuple(
            wrapRow(it),
            it[commentCountColumn],
            if (containColumn != null) (it[containColumn] == 1L) else false,
            it[reactionCountColumn],
            it[aidsValue]
        )
    }) {
        Topics.join(t2, JoinType.LEFT, Topics.id, t2[Topics.parentId])
            .join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
            .join(Reactions, JoinType.LEFT, Topics.id, Reactions.objectId)
            .let {
                when {
                    containColumn != null -> it.select(baseSelection + containColumn)
                    else -> it.select(baseSelection)
                }
            }
            .where(predicate).let(extraPredicate).groupBy(Topics.id)
    }
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

// 加密内容不能处理media，需要客户端处理
@OptIn(ExperimentalStdlibApi::class)
suspend fun DatabaseFactory.getEncryptedTopicContents(
    data: List<TopicInfo>,
    uid: PrimaryKey
) = dbQuery {
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

suspend fun DatabaseFactory.updateTopicStatus(topicId: PrimaryKey, newValue: Boolean): Result<Boolean> {
    return dbQuery {
        Topics.update({
            Topics.id eq topicId
        }) {
            it[pinned] = newValue
        } > 0
    }
}
