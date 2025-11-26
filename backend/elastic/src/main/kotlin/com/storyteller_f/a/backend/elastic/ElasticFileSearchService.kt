package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.FileSearchService
import com.storyteller_f.a.backend.core.service.FileSearchServiceFactory
import com.storyteller_f.shared.type.PrimaryKey
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
        fileDocumentSearch: FileDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<FileDocument>> {
        val boolQuery = buildSearchQuery(fileDocumentSearch, primaryKeyFetch)

        val request = SearchRequest.of { s ->
            s.index(INDEX_NAME)
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
            "elastic search file query $request"
        }
        return useElasticClient {
            searchDocumentList(request, FileDocument::class.java)
        }
    }

    private fun buildSearchQuery(
        fileDocumentSearch: FileDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ) = buildPrimaryKeyElasticSearchQuery(primaryKeyFetch) {
        when (fileDocumentSearch) {
            is FileDocumentSearch.Keyword -> {
                // 添加 ownerId 过滤
                fileDocumentSearch.ownerId?.let { owner ->
                    add(QueryBuilders.term { t ->
                        t.field("ownerId").value(owner)
                    } to true)
                }
                // 添加关键词搜索
                preprocessUserInputKeyword(fileDocumentSearch.word)?.let { v ->
                    add(QueryBuilders.bool { b ->
                        b.should { s ->
                            s.matchPhrasePrefix {
                                it.field("name").query(v).boost(3f)
                            }
                        }
                    } to true)
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
