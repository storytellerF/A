package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.FileDatabase
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ReactionDatabase
import com.storyteller_f.a.backend.core.TopicDatabase
import com.storyteller_f.a.backend.core.types.EncryptedKey
import com.storyteller_f.a.backend.core.types.RawTopic
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.UserFavorite
import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.isNotEmpty
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.EncryptedKeys
import com.storyteller_f.a.backend.exposed.tables.Titles
import com.storyteller_f.a.backend.exposed.tables.Topics
import com.storyteller_f.a.backend.exposed.tables.UserFavorites
import com.storyteller_f.a.backend.exposed.tables.UserSubscriptions
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedTopicDatabase(
    val databaseSession: ExposedDatabaseSession,
    val containerDatabase: ContainerDatabase,
    val fileDatabase: FileDatabase,
    val reactionDatabase: ReactionDatabase,
) : TopicDatabase {
    override suspend fun getTopicRootTuple(parentId: PrimaryKey) = databaseSession.dbSearch {
        search {
            Topics.selectAll().where {
                Topics.id eq parentId
            }
        }
        first {
            val topic = Topic.wrapRow(it)
            ObjectTuple(
                topic.rootId,
                topic.rootType,
            )
        }
    }

    override suspend fun getRawTopic(
        fetch: ObjectFetch,
        uid: PrimaryKey?,
    ) = databaseSession.dbSearch {
        search {
            Topics.join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
                .select(Topics.fields + Aids.value).where {
                    when (fetch) {
                        is ObjectFetch.IdFetch -> Topics.id eq fetch.id
                        is ObjectFetch.AidFetch -> Aids.value eq fetch.aid
                    }
                }
        }
        first(Topic::wrapRow)
    }.mapResultIfNotNull { topic ->
        processTopicToRawTopic(uid, listOf(topic))
    }.mapIfNotNull {
        it.first()
    }

    suspend fun getTopicInfoListByPredicate(
        uid: PrimaryKey?,
        queryBuilder: Query.() -> Query,
    ) = databaseSession.dbSearch {
        search {
            Topics.join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
                .select(Topics.fields + Aids.value)
                .queryBuilder()
        }
        map(Topic::wrapRow)
    }.mapResult {
        processTopicToRawTopic(uid, it)
    }

    override suspend fun getRawTopicListByIds(
        uid: PrimaryKey?,
        ids: List<PrimaryKey>
    ) = getTopicInfoListByPredicate(uid) {
        where {
            Topics.id inList ids
        }
    }

    suspend fun getTopicInfoPaginationByPredicate(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        extraQuery: Query.() -> Query,
    ) = runCatching {
        val rawTopics = getTopicInfoListByPredicate(uid) {
            extraQuery().bindPaginationQuery(Topics, primaryKeyFetch)
        }.getOrThrow()
        val total = databaseSession.dbSearch {
            search {
                Topics
                    .selectAll()
                    .extraQuery()
            }
            count()
        }.getOrThrow()
        PaginationResult(rawTopics, total)
    }

    override suspend fun getRawTopicByParentId(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        parentId: PrimaryKey,
        pinType: TopicPinSearch?
    ) = getTopicInfoPaginationByPredicate(uid, primaryKeyFetch) { ->
        where {
            Topics.parentId eq parentId
        }
        when (pinType) {
            TopicPinSearch.PINNED -> andWhere {
                Topics.pinned eq true
            }

            TopicPinSearch.UNPINNED -> andWhere {
                Topics.pinned eq false
            }

            else -> {
                orderBy(Topics.pinned to SortOrder.DESC)
            }
        }
    }

    override suspend fun getLatestRawTopic(
        uid: PrimaryKey?,
        parentId: PrimaryKey
    ) = getTopicInfoListByPredicate(uid) {
        where {
            Topics.parentId eq parentId
        }.orderBy(Topics.pinned to SortOrder.DESC)
            .bindPaginationQuery(Topics, PrimaryKeyFetch(null, 2))
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun saveEncryptedTopic(
        topic: Topic,
        content: TopicContent.Encrypted,
    ) = databaseSession.dbQuery {
        Topic.new(topic)
        EncryptedKeys.batchInsert(content.encryptedKey.keys) {
            this[EncryptedKeys.topicId] = topic.id
            this[EncryptedKeys.uid] = it
            this[EncryptedKeys.encryptedAes] =
                ExposedBlob(content.encryptedKey[it]!!.hexToByteArray())
        }
        Unit
    }

    override suspend fun savePlainTopic(
        topic: Topic,
        content: TopicContent.Plain
    ) = databaseSession.dbQuery {
        Topic.new(topic)
        fileDatabase.insertFileRefs(
            topic.id,
            ObjectType.TOPIC,
            extractMarkdownMediaLink(content.plain).map {
                topic.author to it
            }
        ).getOrThrow()
    }

    override suspend fun updateTopicStatus(
        topicId: PrimaryKey,
        newValue: Boolean,
    ) = databaseSession.dbQuery {
        Topics.update({
            Topics.id eq topicId
        }) {
            it[Topics.pinned] = newValue
        } > 0
    }

    override suspend fun getTopicList(
        primaryKeyFetch: PrimaryKeyFetch
    ) = databaseSession.dbSearch {
        search {
            Topics.selectAll().bindPaginationQuery(Topics, primaryKeyFetch)
        }
        map(Topic::wrapRow)
    }

    override suspend fun getTopicCommentCount(
        topicIdList: List<PrimaryKey>,
    ): Result<List<Pair<Long, Long>>> {
        if (topicIdList.isEmpty()) return Result.success(emptyList())
        return databaseSession.dbSearch {
            val countColumn = Topics.id.countDistinct()
            search {
                Topics.select(countColumn, Topics.parentId).where {
                    Topics.parentId inList topicIdList
                }.groupBy(Topics.parentId)
            }
            map {
                it[Topics.parentId] to it[countColumn]
            }
        }
    }

    override suspend fun isUserCommented(
        uid: PrimaryKey,
        topicId: List<PrimaryKey>,
    ): Result<List<Long>> {
        if (topicId.isEmpty()) return Result.success(emptyList())
        return databaseSession.dbSearch {
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

    suspend fun processTopicToRawTopic(
        uid: PrimaryKey?,
        topics: List<Topic>
    ): Result<List<RawTopic>> {
        val topicIds = topics.map {
            it.id
        }
        if (topicIds.isEmpty()) return Result.success(emptyList())
        return runCatching {
            val commentedMap = getUserCommentMap(uid, topicIds)
            val commentCountMap = getCommentCountMap(topicIds)
            val reactionCountMap = getReactionCountMap(topicIds)
            val lastReadMap = getLastReadMap(uid, topicIds)
            val contentMap = getTopicContentFromByteArray(topics, uid).getOrThrow()
            val favoriteMap = if (uid != null) {
                getHasFavorite(
                    ObjectListFetch.IdListFetch(topicIds),
                    uid
                ).getOrThrow().associateBy { it.objectId }
            } else {
                emptyMap()
            }
            val subscriptionMap = if (uid != null) {
                getHasSubscription(
                    ObjectListFetch.IdListFetch(topicIds),
                    uid
                ).getOrThrow().associateBy { it.objectId }
            } else {
                emptyMap()
            }
            topics.map { topic ->
                val id = topic.id
                RawTopic(
                    topic,
                    contentMap[id] ?: TopicContent.Nil,
                    commentCountMap[id] ?: 0,
                    commentedMap.contains(id),
                    reactionCountMap[id] ?: 0,
                    lastReadMap[id]?.topicId,
                    favoriteId = favoriteMap[id]?.id,
                    subscriptionId = subscriptionMap[id]?.id,
                )
            }
        }
    }

    private suspend fun getReactionCountMap(topicIds: List<PrimaryKey>) =
        reactionDatabase.getReactionCount(topicIds).map {
            it.associateByPair()
        }.getOrThrow()

    private suspend fun getLastReadMap(
        uid: PrimaryKey?,
        topicIds: List<PrimaryKey>
    ) = if (uid != null) {
        containerDatabase.getTopicReadList(topicIds, uid)
            .map {
                it.associateBy { userTopicRead ->
                    userTopicRead.objectId
                }
            }
    } else {
        Result.success(emptyMap())
    }.getOrThrow()

    private suspend fun getCommentCountMap(topicIds: List<PrimaryKey>) =
        getTopicCommentCount(topicIds).map {
            it.associateByPair()
        }.getOrThrow()

    private suspend fun getUserCommentMap(
        uid: PrimaryKey?,
        topicIds: List<PrimaryKey>
    ) = if (uid != null) {
        isUserCommented(uid, topicIds).map {
            it.toSet()
        }
    } else {
        Result.success(emptySet())
    }.getOrThrow()

    override suspend fun getTopicContentFromByteArray(
        topics: List<Topic>,
        uid: PrimaryKey?,
    ): Result<Map<PrimaryKey, TopicContent>> {
        val encryptedTopic = topics.filter {
            it.isEncrypted
        }
        return runCatching {
            val encryptedTopicList = if (encryptedTopic.isNotEmpty() && uid != null) {
                getEncryptedTopicContents(encryptedTopic, uid).getOrThrow()
            } else {
                emptyList()
            }
            val unEncryptedTopicList = topics.filter { topic ->
                !topic.isEncrypted
            }.map { topic ->
                topic.id to TopicContent.Plain(topic.content.decodeToString())
            }
            (encryptedTopicList + unEncryptedTopicList).associateByPair()
        }
    }

    // 加密内容不能处理media，需要客户端处理
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun getEncryptedTopicContents(
        data: List<Topic>,
        uid: PrimaryKey,
    ): Result<List<Pair<PrimaryKey, TopicContent>>> {
        val topicId = data.map {
            it.id
        }
        return databaseSession.dbSearch {
            search {
                EncryptedKeys.selectAll().where {
                    EncryptedKeys.topicId inList topicId and (EncryptedKeys.uid eq uid)
                }
            }
            map {
                EncryptedKey.wrapRow(it)
            }
        }.map { list ->
            val aesMap = list.associate {
                it.topicId to mapOf(it.uid to it.encryptedAes.toHexString())
            }
            data.map {
                val map = aesMap[it.id] ?: emptyMap()
                val content = it.content.toHexString()
                it.id to TopicContent.Encrypted(content, map)
            }
        }
    }

    override suspend fun createTitle(
        title: Title,
        topic: Topic
    ) = databaseSession.dbQuery {
        check(Titles.insert {
            it[Titles.id] = title.id
            it[Titles.createdTime] = title.createdTime
            it[Titles.name] = title.name
            it[Titles.creator] = title.creator
            it[Titles.receiver] = title.receiver
            it[Titles.type] = title.type
            it[Titles.scopeId] = title.scopeId
            it[Titles.scopeType] = title.scopeType
            it[Titles.status] = title.status
            it[Titles.descriptionTopicId] = title.descriptionTopicId
        }.insertedCount > 0) {
            "insert title failed"
        }
        Topic.new(topic)
    }

    override suspend fun getTopicCount() = databaseSession.dbSearch {
        search {
            Topics.selectAll()
        }
        count()
    }

    override suspend fun getAllTopics(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<Topic>> {
        return databaseSession.dbSearch {
            search {
                Topics.selectAll().bindPaginationQuery(Topics, primaryKeyFetch)
            }
            map {
                Topic.wrapRow(it)
            }
        }.map {
            PaginationResult(it, 0)
        }
    }

    override suspend fun getAllRawTopics(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<RawTopic>> {
        return runCatching {
            val topics = databaseSession.dbSearch {
                search {
                    Topics.selectAll().bindPaginationQuery(Topics, primaryKeyFetch)
                }
                map {
                    Topic.wrapRow(it)
                }
            }.getOrThrow()
            val total = databaseSession.dbSearch {
                search {
                    Topics.selectAll().bindPaginationQuery(Topics, primaryKeyFetch)
                }
                count()
            }.getOrThrow()
            val rawTopic = processTopicToRawTopic(null, topics).getOrThrow()
            PaginationResult(rawTopic, total)
        }
    }

    suspend fun getHasFavorite(
        idList: ObjectListFetch.IdListFetch,
        uid: PrimaryKey
    ) = databaseSession.dbSearch {
        search {
            UserFavorites.selectAll().where {
                (UserFavorites.uid eq uid) and (UserFavorites.objectId inList idList.idList)
            }
        }
        map {
            UserFavorite.wrapRow(it)
        }
    }

    suspend fun getHasSubscription(
        idList: ObjectListFetch.IdListFetch,
        uid: PrimaryKey
    ) = databaseSession.dbSearch {
        search {
            UserSubscriptions.selectAll().where {
                (UserSubscriptions.uid eq uid) and (UserSubscriptions.objectId inList idList.idList)
            }
        }
        map {
            UserSubscription.wrapRow(it)
        }
    }
}

private suspend fun Topic.Companion.new(info: Topic) = check(Topics.insert {
    it[id] = info.id
    it[author] = info.author
    it[createdTime] = now()
    it[parentType] = info.parentType
    it[parentId] = info.parentId
    it[rootId] = info.rootId
    it[rootType] = info.rootType
    it[content] = ExposedBlob(info.content)
    it[isEncrypted] = info.isEncrypted
    it[level] = info.level
}.insertedCount > 0) {
    "insert topic failed"
}
