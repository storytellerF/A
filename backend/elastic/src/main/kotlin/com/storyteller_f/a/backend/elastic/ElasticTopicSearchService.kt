package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.*
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchServiceFactory
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.github.aakira.napier.Napier

class ElasticTopicSearchService(connection: ElasticConnection) : Elastic(connection),
    TopicSearchService {

    companion object {
        private const val INDEX_NAME = "topics"
    }

    override suspend fun saveDocument(documents: List<TopicDocument>): Result<Unit> {
        if (documents.isEmpty()) return UNIT_RESULT
        return useElasticClient {
            saveDocumentList(connection, documents, INDEX_NAME)
        }
    }

    override suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        if (idList.isEmpty()) return Result.success(emptyList())
        return useElasticClient {
            getDocumentList(idList, INDEX_NAME, TopicDocument::class.java)
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useElasticClient {
            cleanAll(INDEX_NAME)
        }
    }

    override suspend fun searchDocument(
        topicDocumentSearch: TopicDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?,
    ): Result<PaginationResult<TopicDocument>> {
        val boolQuery = buildSearchQuery(topicDocumentSearch, primaryKeyFetch)

        // 构建排序条件：按 ID 升序排序
        val request = SearchRequest.of { s ->
            s.index(INDEX_NAME) // 指定索引名称
                .query(boolQuery)
                .size(primaryKeyFetch?.size ?: 10)
                .sort { sort ->
                    sort.field { f ->
                        val sortOrder = when {
                            primaryKeyFetch == null || primaryKeyFetch.cursor == null -> null
                            primaryKeyFetch.cursor is Cursor.AscCursor<PrimaryKey> -> SortOrder.Asc
                            else -> null
                        } ?: SortOrder.Desc
                        f.field("id").order(sortOrder)
                    }
                }
        }
        Napier.i {
            "elastic search query $request"
        }
        return useElasticClient {
            searchDocumentList(request, TopicDocument::class.java)
        }
    }

    private fun buildSearchQuery(
        topicDocumentSearch: TopicDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?,
    ): Query {
        return buildPrimaryKeyElasticSearchQuery(primaryKeyFetch) {
            when (topicDocumentSearch) {
                is TopicDocumentSearch.Recommend -> {
                    addParentIdListTermSearch(topicDocumentSearch.communities)
                    addTermQuery("author", topicDocumentSearch.uid)
                }

                TopicDocumentSearch.RecommendNotLogin -> {
                    addParentTypeTermSearch()
                }

                is TopicDocumentSearch.CommunityRoot -> {
                    addMatchQuery(topicDocumentSearch.word, "content")
                    addParentTypeTermSearch()
                }

                is TopicDocumentSearch.Topics -> {
                    addTermQuery("parentId", topicDocumentSearch.parentId)
                    addMatchQuery(topicDocumentSearch.word, "content")
                }

                is TopicDocumentSearch.All -> {
                    addMatchQuery(topicDocumentSearch.word, "content")
                }
            }
        }
    }

    private fun MutableList<Pair<Query, Boolean>>.addParentIdListTermSearch(
        longs: List<PrimaryKey>
    ) {
        add(TermsQuery.of {
            it.field("parentId").terms { builder ->
                builder.value(longs.map { id ->
                    FieldValue.of(id)
                })
            }
        }._toQuery() to true)
    }

    private fun MutableList<Pair<Query, Boolean>>.addParentTypeTermSearch() {
        add(TermQuery.of { t ->
            t.field("parentType.keyword").value(ObjectType.COMMUNITY.name)
        }._toQuery() to true)
    }
}

class ElasticTopicSearchServiceFactory : TopicSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "elastic"
    }

    override fun build(env: MergedEnv): TopicSearchService {
        return buildElasticSearchService(env) {
            ElasticTopicSearchService(it)
        }
    }
}
