package com.storyteller_f.tables

import com.storyteller_f.BaseObj
import com.storyteller_f.BaseTable
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.bindPaginationQuery
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.objectType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.Tuple4
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Topic.Companion.wrapRow
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.longLiteral
import org.jetbrains.exposed.sql.selectAll

object Topics : BaseTable() {
    val author = customPrimaryKey("author").index()
    val parentId = customPrimaryKey("parent_id")
    val parentType = objectType("parent_type")
    val rootId = customPrimaryKey("root_id")
    val rootType = objectType("root_type")
    val lastModifiedTime = datetime("last_modified_time").nullable()

    init {
        index("topic-main", false, parentType, parentId)
        index("topic-root", false, rootType, rootId)
    }
}

class Topic(
    val author: PrimaryKey,
    val parentId: PrimaryKey,
    val parentType: ObjectType,
    val rootId: PrimaryKey,
    val rootType: ObjectType,
    val lastModifiedTime: LocalDateTime?,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Topic {
            return Topic(
                row[Topics.author],
                row[Topics.parentId],
                row[Topics.parentType],
                row[Topics.rootId],
                row[Topics.rootType],
                row[Topics.lastModifiedTime],
                row[Topics.id],
                row[Topics.createdTime]
            )
        }

        fun findById(topicId: PrimaryKey) = Topics.selectAll().where {
            Topics.id eq topicId
        }

        fun new(info: Topic): PrimaryKey {
            val newTopicId = Topics.insert {
                it[id] = info.id
                it[author] = info.author
                it[createdTime] = now()
                it[parentType] = info.parentType
                it[parentId] = info.parentId
                it[rootId] = info.rootId
                it[rootType] = info.rootType
            }[Topics.id]
            assert(info.id == newTopicId)
            return newTopicId
        }
    }
}

suspend fun getTopicRoot(parentId: PrimaryKey): Result<Pair<PrimaryKey, ObjectType>?> = DatabaseFactory.first({
    rootId to rootType
}, Topic::wrapRow) {
    Topic.findById(parentId)
}

suspend fun getSimpleTopic(topicId: PrimaryKey): Result<TopicInfo?> = DatabaseFactory.first({
    toTopicInfo(0, false)
}, Topic::wrapRow) {
    Topic.findById(topicId)
}

fun Topic.toTopicInfo(commentCount: Long = 0, hasComment: Boolean = false, reactionCount: Long = 0): TopicInfo {
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
        commentCount = commentCount,
        reactionCount = reactionCount,
        hasComment = hasComment,
        lastModifiedTime = now()
    )
}

suspend fun getCommonTopic(topicId: PrimaryKey, uid: PrimaryKey?): Result<TopicInfo?> {
    val t2 = Topics.alias("t2")
    val commentCount = t2[Topics.id].countDistinct()
    val reactionComment = Reactions.id.countDistinct()
    val containExpression = topicAuthorContains(uid, t2)

    val baseSelection = Topics.fields + commentCount + reactionComment
    return DatabaseFactory.first({
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
            .join(Reactions, JoinType.INNER, Topics.id, Reactions.objectId)
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
    val containExpression = if (uid != null) Expression.build {
        val expr = case().When(t2[Topics.author].eq(uid), longLiteral(1)).Else(longLiteral(0))
        Max<Long, Long>(expr, LongColumnType())
    } else {
        null
    }
    return containExpression
}

suspend fun commonPaginationSearchTopics(
    uid: PrimaryKey?,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    fillHasCommented: Boolean?
): Result<Pair<List<TopicInfo>, Long>> {
    return commonSearchTopics(uid, predicate, preTopicId, nextTopicId, size, fillHasCommented).mapResult { data ->
        DatabaseFactory.count {
            Topics
                .selectAll()
                .where(predicate)
        }.map { count ->
            data to count
        }
    }
}

suspend fun commonSearchTopics(
    uid: PrimaryKey?,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    fillHasCommented: Boolean?
): Result<List<TopicInfo>> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    val t2 = Topics.alias("t2")
    val reactionComment = Reactions.id.countDistinct()
    val containExpression = topicAuthorContains(uid, t2)
    val commentCount = t2[Topics.id].countDistinct()
    val baseSelection = Topics.fields + commentCount + reactionComment
    return DatabaseFactory.mapQuery({
        data1.toTopicInfo(data2, data3, data4)
    }, {
        Tuple4(
            wrapRow(it),
            it[commentCount],
            if (containExpression != null) (it[containExpression] == 1L) else false,
            it[reactionComment]
        )
    }) {
        Topics.join(t2, JoinType.LEFT, Topics.id, t2[Topics.parentId])
            .join(Reactions, JoinType.INNER, Topics.id, Reactions.objectId)
            .let {
                when {
                    containExpression != null -> it.select(baseSelection + containExpression)
                    else -> it.select(baseSelection)
                }
            }
            .where(predicate).groupBy(Topics.id).bindPaginationQuery(Topics, preTopicId, nextTopicId, size)
    }
}
