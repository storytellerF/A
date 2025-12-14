package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.service.UserDocumentSearch
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.core.service.UserSearchServiceFactory
import io.github.aakira.napier.Napier

class ElasticUserSearchService(connection: ElasticConnection) : Elastic(connection),
    UserSearchService {
    companion object {
        const val INDEX_NAME = "users"
    }
    override suspend fun saveDocument(documents: List<UserDocument>): Result<Unit> {
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
        userDocumentSearch: UserDocumentSearch
    ): Result<PaginationResult<UserDocument>> {
        if (userDocumentSearch is UserDocumentSearch.Keyword && userDocumentSearch.word.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val request = buildSearchRequest(userDocumentSearch)
        Napier.i {
            "elastic search query $request"
        }
        return useElasticClient {
            searchDocumentList(request, UserDocument::class.java)
        }
    }

    private fun buildSearchRequest(
        userDocumentSearch: UserDocumentSearch
    ): SearchRequest {
        return SearchRequest.of { s ->
            s.index(INDEX_NAME).apply {
                when (userDocumentSearch) {
                    is UserDocumentSearch.Keyword -> {
                        val fetch = userDocumentSearch.fetch
                        from(fetch.cursor?.value ?: 0)
                        size(fetch.size)
                        val word = userDocumentSearch.word
                        query { q ->
                            q.bool { b ->
                                val keyword = preprocessUserInputKeyword(word)
                                b.should { s ->
                                    s.matchPhrasePrefix {
                                        it.field("nickname").query(keyword).boost(3f)
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

class ElasticUserSearchServiceFactory : UserSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "elastic"
    }

    override fun build(env: MergedEnv): UserSearchService {
        return buildElasticSearchService(env) {
            ElasticUserSearchService(it)
        }
    }
}
