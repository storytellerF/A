package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.ObjectFetch
import com.storyteller_f.backend.service.bindPaginationQuery
import com.storyteller_f.backend.service.count
import com.storyteller_f.backend.service.first
import com.storyteller_f.backend.service.map
import com.storyteller_f.backend.service.tables.Aids
import com.storyteller_f.backend.service.tables.EncryptedTopic
import com.storyteller_f.backend.service.tables.EncryptedTopicKey
import com.storyteller_f.backend.service.tables.EncryptedTopicKeys
import com.storyteller_f.backend.service.tables.EncryptedTopics
import com.storyteller_f.backend.service.tables.Topic
import com.storyteller_f.backend.service.tables.Topics
import com.storyteller_f.backend.service.tables.toTopicInfo
import com.storyteller_f.backend.service.types.PaginationResult
import com.storyteller_f.backend.service.types.PrimaryKeyFetch
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.merge
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import kotlin.collections.plus
import kotlin.map

suspend fun ExposedDatabaseSession.getTopicRootTuple(
    parentId: PrimaryKey
): Result<ObjectTuple?> = dbSearch {
    search {
        Topic.Companion.findById(parentId)
    }
    first {
        val topic = Topic.Companion.wrapRow(it)
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
        first(Topic.Companion::wrapRow)
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
    queryBuilder: Query.() -> Query
) = dbSearch {
    search {
        Topics.join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
            .select(Topics.fields + Aids.value)
            .queryBuilder()
    }
    map(Topic.Companion::wrapRow)
}.mapResult {
    processTopicToTopicInfo(uid, it)
}

suspend fun ExposedDatabaseSession.getTopicPaginationResultByPredicate(
    uid: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch,
    extraQuery: Query.() -> Query
): Result<PaginationResult<TopicInfo>> {
    return getTopicInfoListByPredicate(uid, {
        extraQuery().bindPaginationQuery(Topics, primaryKeyFetch)
    }).mapResult { data ->
        dbSearch {
            search {
                Topics
                    .selectAll()
                    .extraQuery()
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
        EncryptedTopicKey.Companion.wrapRow(it)
    }.associate {
        it.topicId to mapOf((it.uid to it.encryptedAes.toHexString()))
    }
    val contentMap = EncryptedTopics.selectAll().where {
        EncryptedTopics.topicId inList topicId
    }.map {
        EncryptedTopic.Companion.wrapRow(it)
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
    Topic.Companion.new(topic)
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
            it[Topics.pinned] = newValue
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
        map(Topic.Companion::wrapRow)
    }
}
