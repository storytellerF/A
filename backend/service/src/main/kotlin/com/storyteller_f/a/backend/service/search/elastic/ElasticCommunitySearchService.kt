package com.storyteller_f.a.backend.service.search.elastic

import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.service.search.CommunityDocument
import com.storyteller_f.a.backend.service.search.CommunityDocumentSearch
import com.storyteller_f.a.backend.service.search.CommunitySearchService
import com.storyteller_f.a.backend.service.search.Elastic
import com.storyteller_f.a.backend.service.search.buildPrimaryKeyElasticSearchQuery
import com.storyteller_f.a.backend.service.search.cleanAll
import com.storyteller_f.a.backend.service.search.preprocessUserInputKeyword
import com.storyteller_f.a.backend.service.search.saveDocumentList
import com.storyteller_f.a.backend.service.search.searchDocumentList
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.github.aakira.napier.Napier
import kotlinx.coroutines.future.await

class ElasticCommunitySearchService(connection: ElasticConnection) :
    Elastic(connection), CommunitySearchService {
    companion object {
        private const val INDEX_NAME = "topics"

    }

    override suspend fun saveDocument(documents: List<CommunityDocument>): Result<Unit> {
        if (documents.isEmpty()) return UNIT_RESULT
        return useElasticClient {
            saveDocumentList(documents, INDEX_NAME)
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useElasticClient {
            cleanAll(INDEX_NAME)
        }
    }

    override suspend fun searchDocument(
        communityDocumentSearch: CommunityDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<CommunityDocument>> {
        val boolQuery = buildSearchQuery(communityDocumentSearch, primaryKeyFetch)

        // 构建排序条件：按 ID 升序排序
        val request = SearchRequest.of { s ->
            s.index(INDEX_NAME) // 指定索引名称
                .query(boolQuery)
                .size(primaryKeyFetch?.size ?: 10)
                .sort { sort ->
                    sort.field { f ->
                        val sortOrder = when {
                            primaryKeyFetch == null || primaryKeyFetch.cursor == null -> null
                            primaryKeyFetch.cursor is Cursor.PreCursor<PrimaryKey> -> SortOrder.Asc
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
            searchDocumentList(request, CommunityDocument::class.java)
        }
    }

    private fun buildSearchQuery(
        communityDocumentSearch: CommunityDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Query {
        return buildPrimaryKeyElasticSearchQuery(primaryKeyFetch) {
            when (communityDocumentSearch) {
                is CommunityDocumentSearch.Keyword -> {
                    preprocessUserInputKeyword(communityDocumentSearch.keyword)?.let { v ->
                        add(QueryBuilders.bool { b ->
                            b.should { s ->
                                s.match {
                                    it.field("name").query(v).boost(3f)
                                }
                            }.should { s ->
                                s.match { p ->
                                    p.field("aid").query(v).boost(3.0f)
                                }
                            }
                        } to true)
                    }
                }
            }
        }
    }
}