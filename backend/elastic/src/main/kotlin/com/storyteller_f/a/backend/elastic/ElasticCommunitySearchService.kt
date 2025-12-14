package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.CommunityDocumentSearch
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.CommunitySearchServiceFactory
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.github.aakira.napier.Napier

class ElasticCommunitySearchService(connection: ElasticConnection) :
    Elastic(connection), CommunitySearchService {
    companion object {
        private const val INDEX_NAME = "topics"
    }

    override suspend fun saveDocument(documents: List<CommunityDocument>): Result<Unit> {
        if (documents.isEmpty()) return UNIT_RESULT
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
        communityDocumentSearch: CommunityDocumentSearch
    ): Result<PaginationResult<CommunityDocument>> {
        if (communityDocumentSearch is CommunityDocumentSearch.Keyword && communityDocumentSearch.keyword.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val request = buildSearchRequest(communityDocumentSearch)
        Napier.i {
            "elastic search query $request"
        }
        return useElasticClient {
            searchDocumentList(request, CommunityDocument::class.java)
        }
    }

    private fun buildSearchRequest(
        communityDocumentSearch: CommunityDocumentSearch
    ): SearchRequest {
        return SearchRequest.of { s ->
            s.index(INDEX_NAME).apply {
                when (communityDocumentSearch) {
                    is CommunityDocumentSearch.Keyword -> {
                        val fetch = communityDocumentSearch.fetch
                        from(fetch.cursor?.value ?: 0)
                        size(fetch.size)
                        val word = communityDocumentSearch.keyword
                        query { q ->
                            q.bool { b ->
                                val keyword = preprocessUserInputKeyword(word)
                                b.should { s ->
                                    s.match {
                                        it.field("name").query(keyword).boost(3f)
                                    }
                                }.should { s ->
                                    s.match { p ->
                                        p.field("aid").query(keyword).boost(3.0f)
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

class ElasticCommunitySearchServiceFactory : CommunitySearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "elastic"
    }

    override fun build(env: MergedEnv): CommunitySearchService {
        return buildElasticSearchService(env) {
            ElasticCommunitySearchService(it)
        }
    }
}
