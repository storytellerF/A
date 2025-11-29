package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.CombinedDatabase
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.TopicDatabase
import com.storyteller_f.a.backend.core.paginationFromResults
import com.storyteller_f.a.backend.core.types.EncryptedKey
import com.storyteller_f.a.backend.core.types.FileRef
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.EncryptedKeys
import com.storyteller_f.a.backend.exposed.tables.Titles
import com.storyteller_f.a.backend.exposed.tables.Topics
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
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
    val combinedDatabase: CombinedDatabase,
) : TopicDatabase {
    override suspend fun getTopicRootTuple(parentId: PrimaryKey) =
        getTopicByPredicate({
            ObjectTuple(it[Topics.rootId], it[Topics.rootType])
        }) {
            where {
                Topics.id eq parentId
            }
        }

    override suspend fun getTopic(fetch: ObjectFetch): Result<Topic?> =
        getTopicByPredicate(Topic::wrapRow) {
            where {
                when (fetch) {
                    is ObjectFetch.IdFetch -> Topics.id eq fetch.id
                    is ObjectFetch.AidFetch -> Aids.value eq fetch.aid
                }
            }
        }

    private suspend fun <T> getTopicByPredicate(
        block: (ResultRow) -> T,
        extraQuery: Query.() -> Query = { this }
    ): Result<T?> = databaseSession.dbSearch {
        search {
            Topics.join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
                .select(Topics.fields + Aids.value).extraQuery()
        }
        first(block)
    }

    private suspend fun getTopicListByPredicate(
        queryBuilder: Query.() -> Query = { this }
    ): Result<List<Topic>> =
        databaseSession.dbSearch {
            search {
                Topics.join(Aids, JoinType.LEFT, Topics.id, Aids.objectId)
                    .select(Topics.fields + Aids.value)
                    .queryBuilder()
            }
            map(Topic::wrapRow)
        }

    suspend fun getTopicCountByPredicate(
        queryBuilder: Query.() -> Query = { this },
    ) = databaseSession.dbSearch {
        search {
            Topics.selectAll().queryBuilder()
        }
        count()
    }

    override suspend fun getTopicListByIds(ids: List<PrimaryKey>): Result<List<Topic>> =
        getTopicListByPredicate {
            where {
                Topics.id inList ids
            }
        }

    private suspend fun getTopicPaginationByPredicate(
        primaryKeyFetch: PrimaryKeyFetch,
        extraQuery: Query.() -> Query = { this }
    ): Result<PaginationResult<Topic>> = paginationFromResults(getTopicListByPredicate {
        extraQuery().bindPaginationQuery(
            Topics,
            primaryKeyFetch
        )
    }, getTopicCountByPredicate {
        extraQuery()
    })

    override suspend fun getTopicByParentId(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        parentId: PrimaryKey,
        pinType: TopicPinSearch?
    ): Result<PaginationResult<Topic>> = getTopicPaginationByPredicate(primaryKeyFetch) {
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

    override suspend fun getLatestTopic(parentId: PrimaryKey): Result<List<Topic>> =
        getTopicListByPredicate {
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
            this[EncryptedKeys.encryptedAes] = ExposedBlob(content.encryptedKey[it]!!.hexToByteArray())
        }
        Unit
    }

    override suspend fun savePlainTopic(
        topic: Topic,
        content: TopicContent.Plain,
        fileRefs: List<FileRef>
    ) = databaseSession.dbQuery {
        Topic.new(topic)
        combinedDatabase.file.insertFileRefs(fileRefs).getOrThrow()
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
    ) = getTopicListByPredicate {
        bindPaginationQuery(Topics, primaryKeyFetch)
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
            search {
                Topics.select(Topics.parentId).where {
                    Topics.parentId inList topicId and (Topics.author eq uid)
                }
            }
            map {
                it[Topics.parentId]
            }
        }
    }

    override suspend fun getUserCommentedTopicsPaginationResult(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<PaginationResult<Topic>> = getTopicPaginationByPredicate(primaryKeyFetch) {
        where {
            Topics.author eq uid and (Topics.parentType eq ObjectType.TOPIC)
        }
    }

    override suspend fun getUserCommentCount(
        uid: PrimaryKey
    ): Result<Long> = getTopicCountByPredicate {
        where {
            Topics.author eq uid and (Topics.parentType eq ObjectType.TOPIC)
        }
    }

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

    override suspend fun getTopicCount() = getTopicCountByPredicate()

    override suspend fun getAllTopicPagination(primaryKeyFetch: PrimaryKeyFetch):
        Result<PaginationResult<Topic>> = getTopicPaginationByPredicate(primaryKeyFetch)
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
