package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.FileDatabase
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.backend.core.TopicDatabase
import com.storyteller_f.a.backend.core.types.EncryptedKey
import com.storyteller_f.a.backend.core.types.Reaction
import com.storyteller_f.a.backend.core.types.ReactionRecord
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.toTopicInfo
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.isNotEmpty
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildReactionInfoQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.EncryptedKeys
import com.storyteller_f.a.backend.exposed.tables.ReactionRecords
import com.storyteller_f.a.backend.exposed.tables.Reactions
import com.storyteller_f.a.backend.exposed.tables.Titles
import com.storyteller_f.a.backend.exposed.tables.Topics
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.groupByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsert

class ExposedTopicDatabase(
    val databaseSession: ExposedDatabaseSession,
    val containerDatabase: ContainerDatabase,
    val fileDatabase: FileDatabase
) :
    TopicDatabase {
    override suspend fun getTopicRootTuple(
        parentId: PrimaryKey,
    ) = databaseSession.dbSearch {
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

    override suspend fun getTopicInfo(
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
        processTopicToTopicInfo(uid, listOf(topic)).map {
            it.first()
        }
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
        processTopicToTopicInfo(uid, it)
    }

    override suspend fun getTopicInfoListByIds(
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
        val r1 = getTopicInfoListByPredicate(uid) {
            extraQuery().bindPaginationQuery(Topics, primaryKeyFetch)
        }.getOrThrow()
        val r2 = databaseSession.dbSearch {
            search {
                Topics
                    .selectAll()
                    .extraQuery()
            }
            count()
        }.getOrThrow()
        PaginationResult(r1, r2)
    }

    override suspend fun getSubTopicInfo(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        parentId: PrimaryKey,
        pinType: TopicPinSearch?
    ) = getTopicInfoPaginationByPredicate(
        uid,
        primaryKeyFetch
    ) { ->
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

    override suspend fun getLatestTopicInfo(
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
        topic.toTopicInfo(content = content)
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

    override suspend fun processTopicToTopicInfo(
        uid: PrimaryKey?,
        topics: List<Topic>,
    ): Result<List<TopicInfo>> {
        val topicIds = topics.map {
            it.id
        }
        return runCatching {
            val commented = if (uid != null) {
                isUserCommented(uid, topicIds).map {
                    it.toSet()
                }
            } else {
                Result.success(emptySet())
            }.getOrThrow()
            val commentCountMap =
                getTopicCommentCount(topicIds).map {
                    it.associateByPair()
                }.getOrThrow()
            val reactionCountMap =
                getReactionCount(topicIds).map {
                    it.associateByPair()
                }.getOrThrow()
            val lastReadMap = if (uid != null) {
                containerDatabase.getTopicReadList(topicIds, uid)
                    .map {
                        it.associateBy { userTopicRead ->
                            userTopicRead.objectId
                        }
                    }
            } else {
                Result.success(emptyMap())
            }.getOrThrow()
            val contentMap = processByteArrayToTopicContent(
                topics,
                uid
            ).getOrThrow()
            topics.map { topic ->
                val id = topic.id
                topic.toTopicInfo(
                    commentCountMap[id] ?: 0,
                    commented.contains(id),
                    reactionCountMap[id] ?: 0,
                    lastRead = lastReadMap[id]?.topicId,
                    content = contentMap[id]!!
                )
            }
        }
    }

    override suspend fun processByteArrayToTopicContent(
        topics: List<Topic>,
        uid: PrimaryKey?,
    ): Result<Map<PrimaryKey, TopicContent>> {
        val encryptedTopic = topics.filter {
            it.isEncrypted
        }
        return (if (encryptedTopic.isEmpty()) {
            Result.success(emptyList())
        } else if (uid != null) {
            getEncryptedTopicContents(encryptedTopic, uid).map {
                it.mapIndexed { i, e ->
                    topics[i].id to e
                }
            }
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }).map {
            it + topics.filter { topic ->
                !topic.isEncrypted
            }.map { topic ->
                topic.id to TopicContent.Plain(topic.content.decodeToString())
            }
        }.map { list ->
            list.associate { it }
        }
    }

    // 加密内容不能处理media，需要客户端处理
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun getEncryptedTopicContents(
        data: List<Topic>,
        uid: PrimaryKey,
    ): Result<List<TopicContent.Encrypted>> {
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
                TopicContent.Encrypted(content, map)
            }
        }
    }

    override suspend fun statsReactionRecord(
        objectId: PrimaryKey,
        emoji: String,
        objectType: ObjectType,
    ) = getReactionCountForEmoji(listOf(objectId), emoji).mapResult { reactionCountList ->
        val triple = reactionCountList.firstOrNull()
        if (triple == null) {
            databaseSession.dbQuery {
                Reactions.deleteWhere {
                    Reactions.objectId eq objectId and (Reactions.emoji eq emoji)
                }
                Unit
            }
        } else {
            databaseSession.dbQuery {
                check(Reactions.upsert(Reactions.objectId, Reactions.emoji) {
                    it[Reactions.objectId] = objectId
                    it[Reactions.emoji] = emoji
                    it[Reactions.count] = triple.second
                    it[Reactions.objectType] = objectType
                    it[Reactions.lastReactionId] = triple.third
                }.insertedCount > 0) {
                    "insert reaction failed"
                }
            }
        }
    }

    override suspend fun getReactionInfoPaginationResult(
        objectId: List<PrimaryKey>,
        uid: PrimaryKey?,
        reactionFetch: ReactionFetch,
    ) = databaseSession.dbSearch {
        search {
            buildReactionInfoQuery(objectId, reactionFetch).limit(reactionFetch.size)
                .orderBy(
                    Reactions.count to SortOrder.DESC,
                    Reactions.lastReactionId to SortOrder.ASC
                )
        }
        map(Reaction::wrapRow)
    }.mapResult { list ->
        databaseSession.dbSearch {
            search {
                buildReactionInfoQuery(objectId, reactionFetch)
            }
            count()
        }.mapResult { count ->
            processReactionToReactionInfo(uid, list).map { reactionInfos ->
                PaginationResult(reactionInfos, count)
            }
        }
    }

    private suspend fun processReactionToReactionInfo(
        uid: PrimaryKey?,
        list: List<Reaction>
    ) = (if (uid == null) {
        Result.success(emptyMap())
    } else {
        val objectIdList = list.map {
            it.objectId
        }.distinct()
        hasReactedEmoji(objectIdList, uid).map {
            it.groupByPair().mapValues { v ->
                v.value.toSet()
            }
        }
    }).map { reactedMap ->
        list.map {
            ReactionInfo(
                it.emoji,
                it.objectId,
                it.count,
                reactedMap[it.objectId]?.contains(it.emoji) == true,
                it.lastReactionId
            )
        }
    }

    override suspend fun hasReactedEmoji(
        objectIdList: List<PrimaryKey>,
        uid: PrimaryKey,
    ): Result<List<Pair<Long, String>>> {
        if (objectIdList.isEmpty()) return Result.success(emptyList())
        return databaseSession.dbSearch {
            search {
                ReactionRecords.select(ReactionRecords.objectId, ReactionRecords.emoji).where {
                    (ReactionRecords.objectId inList objectIdList) and (ReactionRecords.uid eq uid)
                }.groupBy(ReactionRecords.objectId, ReactionRecords.emoji)
            }
            map {
                it[ReactionRecords.objectId] to it[ReactionRecords.emoji]
            }
        }
    }

    override suspend fun getReactionInfo(
        uid: PrimaryKey,
        objectId: PrimaryKey,
        emojiText: String,
    ) = databaseSession.dbSearch {
        search {
            Reactions.selectAll().where {
                Reactions.objectId eq objectId and (Reactions.emoji eq emojiText)
            }
        }
        first {
            Reaction.wrapRow(it)
        }
    }.mapResultIfNotNull {
        hasReactedForEmoji(objectId, uid, emojiText).map { hasReacted ->
            ReactionInfo(it.emoji, it.objectId, it.count, hasReacted, it.lastReactionId)
        }
    }

    override suspend fun hasReactedForEmoji(
        objectId: PrimaryKey,
        uid: PrimaryKey,
        emoji: String,
    ) = databaseSession.dbSearch {
        search {
            ReactionRecords.selectAll().where {
                (ReactionRecords.objectId eq objectId) and
                        (ReactionRecords.emoji eq emoji) and
                        (ReactionRecords.uid eq uid)
            }
        }
        isNotEmpty()
    }

    override suspend fun deleteReaction(
        uid: PrimaryKey,
        emoji: String,
        objectId: PrimaryKey,
    ) = getReactionRecordInfo(uid, emoji, objectId).mapResult { recordInfo ->
        if (recordInfo == null) {
            Result.success(true)
        } else {
            deleteReaction(recordInfo.id)
        }
    }

    override suspend fun getReactionRecordInfo(
        uid: PrimaryKey,
        emoji: String,
        objectId: PrimaryKey,
    ) = databaseSession.dbSearch {
        search {
            ReactionRecords.selectAll().where {
                (ReactionRecords.objectId eq objectId) and
                        (ReactionRecords.emoji eq emoji) and
                        (ReactionRecords.uid eq uid)
            }
        }
        first {
            val reactionRecord = ReactionRecord.wrapRow(it)
            ReactionRecordInfo(
                reactionRecord.id,
                emoji,
                reactionRecord.objectId,
                reactionRecord.objectType,
                reactionRecord.createdTime,
                uid
            )
        }
    }

    override suspend fun deleteReaction(
        reactionId: PrimaryKey,
    ) = databaseSession.dbQuery {
        ReactionRecords.deleteWhere {
            ReactionRecords.id eq reactionId
        }
    }.map { value ->
        value > 0
    }

    override suspend fun insertReaction(
        reactionRecord: ReactionRecord,
    ) = databaseSession.dbQuery {
        check(ReactionRecords.insert { statement ->
            statement[ReactionRecords.id] = reactionRecord.id
            statement[ReactionRecords.uid] = reactionRecord.uid
            statement[ReactionRecords.objectId] = reactionRecord.objectId
            statement[ReactionRecords.objectType] = reactionRecord.objectType
            statement[ReactionRecords.emoji] = reactionRecord.emoji
            statement[ReactionRecords.createdTime] = reactionRecord.createdTime
        }.insertedCount > 0) {
            "insert reaction failed"
        }
    }

    override suspend fun getReactionCount(objectIdList: List<PrimaryKey>): Result<List<Pair<Long, Long>>> {
        if (objectIdList.isEmpty()) return Result.success(emptyList())
        return databaseSession.dbSearch {
            search {
                Reactions.selectAll().where {
                    Reactions.objectId inList objectIdList
                }
            }
            map {
                it[Reactions.objectId] to it[Reactions.count]
            }
        }
    }

    override suspend fun getReactionCountForEmoji(
        objectId: List<PrimaryKey>,
        emoji: String,
    ) = databaseSession.dbSearch {
        val column = ReactionRecords.emoji.countDistinct()
        val lastReactionId = ReactionRecords.id.max()
        search {
            ReactionRecords.select(ReactionRecords.objectId, column, lastReactionId).where {
                (ReactionRecords.objectId inList objectId) and (ReactionRecords.emoji eq emoji)
            }.groupBy(ReactionRecords.objectId)
        }
        map {
            Triple(it[ReactionRecords.objectId], it[column], it[lastReactionId] ?: 0)
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
}
