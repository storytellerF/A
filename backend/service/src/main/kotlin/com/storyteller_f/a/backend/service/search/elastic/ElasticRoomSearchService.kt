package com.storyteller_f.a.backend.service.search.elastic

import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.service.search.Elastic
import com.storyteller_f.a.backend.service.search.RoomDocument
import com.storyteller_f.a.backend.service.search.RoomDocumentSearch
import com.storyteller_f.a.backend.service.search.RoomSearchService
import com.storyteller_f.a.backend.service.search.buildPrimaryKeyElasticSearchQuery
import com.storyteller_f.a.backend.service.search.cleanAll
import com.storyteller_f.a.backend.service.search.preprocessUserInputKeyword
import com.storyteller_f.a.backend.service.search.saveDocumentList
import com.storyteller_f.a.backend.service.search.searchDocumentList
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier

class ElasticRoomSearchService(connection: ElasticConnection) : Elastic(connection), RoomSearchService {
    companion object {
        const val INDEX_NAME = "rooms"
    }
    override suspend fun saveDocument(documents: List<RoomDocument>): Result<Unit> {
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
        roomDocumentSearch: RoomDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<RoomDocument>> {
        val boolQuery = buildSearchQuery(roomDocumentSearch, primaryKeyFetch)

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
            searchDocumentList(request, RoomDocument::class.java)
        }
    }

    private fun buildSearchQuery(
        roomDocumentSearch: RoomDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Query {
        return buildPrimaryKeyElasticSearchQuery(primaryKeyFetch) {
            when (roomDocumentSearch) {
                is RoomDocumentSearch.Keyword -> {
                    preprocessUserInputKeyword(roomDocumentSearch.words)?.let { v ->
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
