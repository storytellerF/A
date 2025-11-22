package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.RoomSearchServiceFactory
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier

class ElasticRoomSearchService(connection: ElasticConnection) : Elastic(connection),
    RoomSearchService {
    companion object {
        const val INDEX_NAME = "rooms"
    }
    override suspend fun saveDocument(documents: List<RoomDocument>): Result<Unit> {
        return useElasticClient {
            saveDocumentList(connection, documents, INDEX_NAME)
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

class ElasticRoomSearchServiceFactory : RoomSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "elastic"
    }

    override fun build(env: MergedEnv): RoomSearchService {
        return buildElasticSearchService(env) {
            ElasticRoomSearchService(it)
        }
    }
}
