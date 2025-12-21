package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.FileSearchService
import com.storyteller_f.a.backend.core.service.FileSearchServiceFactory
import io.github.aakira.napier.Napier

class ElasticFileSearchService(connection: ElasticConnection) : Elastic(connection),
    FileSearchService {
    companion object {
        const val INDEX_NAME = "files"
    }

    override suspend fun saveDocument(documents: List<FileDocument>): Result<Unit> {
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
        fileDocumentSearch: FileDocumentSearch
    ): Result<PaginationResult<FileDocument>> {
        if (fileDocumentSearch is FileDocumentSearch.Keyword && fileDocumentSearch.word.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val request = buildSearchRequest(fileDocumentSearch)
        Napier.i {
            "elastic search file query $request"
        }
        return useElasticClient {
            searchDocumentList(request, FileDocument::class.java)
        }
    }

    private fun buildSearchRequest(
        fileDocumentSearch: FileDocumentSearch
    ): SearchRequest = SearchRequest.of { s ->
        s.index(INDEX_NAME).apply {
            when (fileDocumentSearch) {
                is FileDocumentSearch.Keyword -> {
                    val fetch = fileDocumentSearch.fetch
                    from(fetch.cursor?.value ?: 0)
                    size(fetch.size)
                    query { q ->
                        q.bool { b ->
                            // 添加 ownerId 过滤
                            fileDocumentSearch.ownerId?.let { owner ->
                                b.filter { f ->
                                    f.term { t ->
                                        t.field("ownerId").value(owner)
                                    }
                                }
                            }
                            // 添加关键词搜索
                            val keyword = preprocessUserInputKeyword(fileDocumentSearch.word)
                            b.prioritizedField(keyword, "name")
                        }
                    }
                }
            }
        }
    }
}

class ElasticFileSearchServiceFactory : FileSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "elastic"
    }

    override fun build(env: MergedEnv): FileSearchService {
        return buildElasticSearchService(env) {
            ElasticFileSearchService(it)
        }
    }
}
