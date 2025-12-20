package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.RoomSearchServiceFactory
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
        roomDocumentSearch: RoomDocumentSearch
    ): Result<PaginationResult<RoomDocument>> {
        if (roomDocumentSearch is RoomDocumentSearch.Keyword && roomDocumentSearch.words.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val request = buildSearchRequest(roomDocumentSearch)
        Napier.i {
            "elastic search query $request"
        }
        return useElasticClient {
            searchDocumentList(request, RoomDocument::class.java)
        }
    }

    private fun buildSearchRequest(
        roomDocumentSearch: RoomDocumentSearch
    ): SearchRequest {
        return SearchRequest.of { s ->
            s.index(INDEX_NAME).apply {
                when (roomDocumentSearch) {
                    is RoomDocumentSearch.Keyword -> {
                        val fetch = roomDocumentSearch.fetch
                        from(fetch.cursor?.value ?: 0)
                        size(fetch.size)
                        val word = roomDocumentSearch.words
                        query { q ->
                            q.bool { b ->
                                val keyword = preprocessUserInputKeyword(word)
                                b.must { m ->
                                    m.bool { nestedBool ->
                                        nestedBool.prioritizedFields(keyword, "aid", "name")
                                    }
                                }
                                // 添加对communityId的过滤
                                roomDocumentSearch.communityId?.let { communityId ->
                                    b.filter { f ->
                                        f.term { t ->
                                            t.field("communityId").value(communityId)
                                        }
                                    }
                                }
                            }
                        }
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
