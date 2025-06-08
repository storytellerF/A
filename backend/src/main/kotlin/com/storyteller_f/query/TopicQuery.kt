package com.storyteller_f.query

import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.ObjectFetch
import com.storyteller_f.bindPaginationQuery
import com.storyteller_f.count
import com.storyteller_f.first
import com.storyteller_f.map
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UnauthorizedException
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.merge
import com.storyteller_f.tables.Aids
import com.storyteller_f.tables.EncryptedTopic
import com.storyteller_f.tables.EncryptedTopicKey
import com.storyteller_f.tables.EncryptedTopicKeys
import com.storyteller_f.tables.EncryptedTopics
import com.storyteller_f.tables.Topic
import com.storyteller_f.tables.Topics
import com.storyteller_f.tables.toTopicInfo
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PrimaryKeyFetch
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.update
import kotlin.text.hexToByteArray
import kotlin.text.orEmpty

suspend fun ExposedDatabaseSession.getTopicRootTuple(
    parentId: PrimaryKey
): Result<ObjectTuple?> = dbSearch {
    search {
        Topic.findById(parentId)
    }
    first {
        val topic = Topic.wrapRow(it)
        ObjectTuple(
            topic.rootId,
            topic.rootType,
        )
    }
}

suspend fun ExposedDatabaseSession.getTopicCommentCount(
    topicIdList: List<PrimaryKey>
): Result<List<Pair<Long, Long>>> {
    if (topicIdList.isEmpty()) return Result.success(emptyList())
    return dbSearch {
        val countColumn = Topics.id.countDistinct()
        search {
            Topics.select(countColumn, Topics.id).where {
                Topics.parentId inList topicIdList
            }.groupBy(Topics.id)
        }
        map {
            it[Topics.id] to it[countColumn]
        }
    }
}

suspend fun ExposedDatabaseSession.isUserCommented(
    uid: PrimaryKey,
    topicId: List<PrimaryKey>
): Result<List<Long>> {
    if (topicId.isEmpty()) return Result.success(emptyList())
    return dbSearch {
        this.search {
            Topics.select(Topics.parentId).where {
                Topics.parentId inList topicId and (Topics.author eq uid)
            }
        }
        map {
            it[Topics.parentId]
        }
    }
}

suspend fun ExposedDatabaseSession.getTopicInfo(
    fetch: ObjectFetch,
    uid: PrimaryKey?
): Result<TopicInfo?> {
    return dbSearch {
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
        processTopicToTopicInfo(uid, listOf(topic)).map {
            it.first()
        }
    }
}

private suspend fun ExposedDatabaseSession.processTopicToTopicInfo(
    uid: PrimaryKey?,
    topics: List<Topic>
): Result<List<TopicInfo>> {
    val topicIds = topics.map {
        it.id
    }
    return merge({
        if (uid != null) {
            isUserCommented(uid, topicIds).map {
                it.toSet()
            }
        } else {
            Result.success(emptySet())
        }
    }, {
        getTopicCommentCount(topicIds).map {
            it.associateByPair()
        }
    }, {
        getReactionCount(topicIds).map {
            it.associateByPair()
        }
    }, {
        if (uid != null) {
            getTopicReadList(topicIds, uid).map {
                it.associateBy { userTopicRead ->
                    userTopicRead.objectId
                }
            }
        } else {
            Result.success(emptyMap())
        }
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
}

/**
 * 根据指定条件获取未填充content 的topic 列表
 */
suspend fun ExposedDatabaseSession.getTopicInfoListByPredicate(
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    addPinOrder: Boolean = false,
    addPagingQuery: Query.() -> Query = { this },
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<List<TopicInfo>> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return dbSearch {
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
        processTopicToTopicInfo(uid, it)
    }
}

suspend fun ExposedDatabaseSession.getTopicPaginationResultByPredicate(
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    primaryKeyFetch: PrimaryKeyFetch,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
): Result<PaginationResult<TopicInfo>> {
    return getTopicInfoListByPredicate(uid, fillHasCommented, addPagingQuery = {
        bindPaginationQuery(Topics, primaryKeyFetch)
    }, predicate = predicate).mapResult { data ->
        dbSearch {
            search {
                Topics
                    .selectAll()
                    .where(predicate)
            }
            count()
        }.map { count ->
            PaginationResult(data, count)
        }
    }
}

// 加密内容不能处理media，需要客户端处理
@OptIn(ExperimentalStdlibApi::class)
suspend fun ExposedDatabaseSession.getEncryptedTopicContents(
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

@OptIn(ExperimentalStdlibApi::class)
suspend fun ExposedDatabaseSession.saveEncryptedTopic(
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
    topic.toTopicInfo(hasComment = false, isPrivate = true)
        .copy(content = TopicContent.Encrypted(content.encrypted, content.encryptedKey), isPrivate = true)
}

suspend fun ExposedDatabaseSession.updateTopicStatus(
    topicId: PrimaryKey,
    newValue: Boolean
): Result<Boolean> {
    return dbQuery {
        Topics.update({
            Topics.id eq topicId
        }) {
            it[pinned] = newValue
        } > 0
    }
}

suspend fun ExposedDatabaseSession.getTopicList(firstId: PrimaryKey): Result<List<Topic>> {
    return dbSearch {
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
