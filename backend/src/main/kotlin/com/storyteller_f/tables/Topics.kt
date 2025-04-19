package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
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

suspend fun DatabaseFactory.getTopicRoot(parentId: PrimaryKey): Result<ObjectTuple?> = first({
    rootId ob rootType
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

suspend fun DatabaseFactory.getTopicInfo(fetch: ObjectFetch, uid: PrimaryKey?): Result<TopicInfo?> {
    val (query, resultRowTransform) = getTopicBuilder(uid)
    return first({
        topicInfo.toTopicInfo(commentCount, hasComment, reactionCount, aid)
    }, resultRowTransform) {
        query.andWhere {
            when (fetch) {
                is ObjectFetch.IdFetch -> Topics.id eq fetch.id
                is ObjectFetch.AidFetch -> Aids.value eq fetch.aid
            }
        }.groupBy(Topics.id)
    }
}

private fun getTopicBuilder(uid: PrimaryKey?): Pair<Query, (ResultRow) -> TopicSearchTuple> {
    val t2 = Topics.alias("t2")
    val t3 = Topics.alias("t3")
    val commentCountColumn = t2[Topics.id].countDistinct()
    val selfCommentColumn = t3[Topics.author].max()
    val reactionCountColumn = Reactions.id.countDistinct()
    val aidsValue = Aids.value.max()
    val baseSelection = Topics.fields + commentCountColumn + reactionCountColumn + aidsValue
    val query = Topics.join(t2, JoinType.LEFT, Topics.id, t2[Topics.parentId])
        .join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
        .join(Reactions, JoinType.LEFT, Topics.id, Reactions.objectId)
        .select(baseSelection)
    if (uid != null) {
        query.adjustColumnSet {
            join(t3, JoinType.LEFT, Topics.id, t3[Topics.parentId]) {
                t3[Topics.author] eq uid
            }
        }.adjustSelect {
            select(baseSelection + selfCommentColumn)
        }
    }

    val resultRowTransform: (ResultRow) -> TopicSearchTuple = {
        TopicSearchTuple(
            wrapRow(it),
            it[commentCountColumn],
            it.getOrNull(selfCommentColumn)?.let {
                it > 0
            } ?: false,
            it[reactionCountColumn],
            it[aidsValue]
        )
    }
    return Pair(query, resultRowTransform)
}

/**
 * 根据指定条件获取未填充content 的topic 列表
 */
suspend fun getTopicsByPredicate(
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    extraPredicate: (Query) -> Query = { it },
    addPinOrder: Boolean = false,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<List<TopicInfo>> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    val (query, resultRowTransform) = getTopicBuilder(uid)
    return DatabaseFactory.mapQuery({
        topicInfo.toTopicInfo(commentCount, hasComment, reactionCount, aid)
    }, resultRowTransform) {
        query.andWhere(predicate).let(extraPredicate)
            .groupBy(*if (addPinOrder) arrayOf(Topics.pinned, Topics.id) else arrayOf(Topics.id))
    }
}

suspend fun getTopicsPagingByPredicate(
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    pagingFetch: PagingFetch,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<PaginationResult<TopicInfo>> {
    return getTopicsByPredicate(uid, fillHasCommented, {
        it.bindPaginationQuery(Topics, pagingFetch)
    }, predicate = predicate).mapResult { data ->
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

suspend fun DatabaseFactory.savePlainTopic(
    topic: Topic,
    backend: Backend,
    content: TopicContent.Plain
) = dbQuery {
    Topic.new(topic)
    insertMediaRefs(topic.id, ObjectType.TOPIC, extractMarkdownMediaLink(content.plain).map {
        topic.author to it
    }).getOrThrow()

    backend.topicSearchService.saveDocument(
        listOf(TopicDocument.fromTopic(topic, content))
    ).getOrThrow()
    topic.toTopicInfo().copy(content = content)
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun DatabaseFactory.saveEncryptedTopic(
    topic: Topic,
    content: TopicContent.Encrypted,
) = dbQuery {
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
    topic.toTopicInfo(hasComment = false)
        .copy(content = TopicContent.Encrypted(content.encrypted, content.encryptedKey), isPrivate = true)
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
