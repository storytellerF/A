package com.storyteller_f.a.backend.service.search.lucene

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.service.search.Lucene
import com.storyteller_f.a.backend.service.search.UserDocument
import com.storyteller_f.a.backend.service.search.UserDocumentSearch
import com.storyteller_f.a.backend.service.search.UserSearchService
import com.storyteller_f.a.backend.service.search.buildPrimaryKeyLuceneSearchQuery
import com.storyteller_f.a.backend.service.search.cleanAll
import com.storyteller_f.a.backend.service.search.preprocessUserInputKeyword
import com.storyteller_f.a.backend.service.search.saveDocumentList
import com.storyteller_f.a.backend.service.search.searchDocumentList
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import java.nio.file.Path

class LuceneUserSearchService(path: Path, isInMemory: Boolean = false) : Lucene(path, isInMemory), UserSearchService {
    override suspend fun saveDocument(documents: List<UserDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents, analyzer)
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            cleanAll(analyzer)
        }
    }

    override suspend fun searchDocument(
        userDocumentSearch: UserDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<UserDocument>> {
        val combinedQuery = buildQuery(primaryKeyFetch, userDocumentSearch)
        val reverse = when {
            primaryKeyFetch == null || primaryKeyFetch.cursor == null -> true
            primaryKeyFetch.cursor is Cursor.NextCursor<PrimaryKey> -> true
            else -> false
        }
        val sortById = Sort(SortField("id2", SortField.Type.LONG, reverse))
        Napier.i {
            "lucene search query $combinedQuery $sortById $reverse"
        }
        return useLucene {
            searchDocumentList(combinedQuery, primaryKeyFetch, sortById, UserDocument)
        }
    }

    private fun buildQuery(
        primaryKeyFetch: PrimaryKeyFetch?,
        userDocumentSearch: UserDocumentSearch
    ): Query {
        return buildPrimaryKeyLuceneSearchQuery(primaryKeyFetch) {
            when (userDocumentSearch) {
                is UserDocumentSearch.Keyword -> {
                    preprocessUserInputKeyword(userDocumentSearch.word)?.let {
                        add(BooleanQuery.Builder().apply {
                            add(
                                MultiFieldQueryParser(
                                    arrayOf("nickname"),
                                    analyzer
                                ).parse(it),
                                BooleanClause.Occur.SHOULD
                            )
                            add(
                                MultiFieldQueryParser(
                                    arrayOf("aid"),
                                    analyzer
                                ).parse(it),
                                BooleanClause.Occur.SHOULD
                            )
                        }.build(), BooleanClause.Occur.MUST)
                    }
                }
            }
        }
    }
}
