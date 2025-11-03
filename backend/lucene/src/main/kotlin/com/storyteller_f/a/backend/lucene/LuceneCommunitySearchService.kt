package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.CommunityDocumentSearch
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.CommunitySearchServiceFactory
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import io.github.aakira.napier.Napier
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.TextField
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import java.nio.file.Path

class LuceneCommunityDocument(val communityDocument: CommunityDocument) : LuceneDocument {
    override fun save(): Document {
        val id = communityDocument.id
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("name", communityDocument.name, Field.Store.YES))
            add(TextField("aid", communityDocument.aid, Field.Store.YES))
            add(LongField("owner", communityDocument.owner, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<CommunityDocument> {

        override fun restore(
            id: PrimaryKey,
            document: Document
        ): CommunityDocument {
            return CommunityDocument(
                id,
                document.get("name"),
                document.get("aid"),
                document.get("owner").toPrimaryKey()
            )
        }
    }
}

class LuceneCommunitySearchService(
    path: Path,
    isInMemory: Boolean = false
) : Lucene(path, isInMemory), CommunitySearchService {
    override suspend fun saveDocument(documents: List<CommunityDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents.map {
                LuceneCommunityDocument(it)
            }, analyzer)
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            cleanAll(analyzer)
        }
    }

    override suspend fun searchDocument(
        communityDocumentSearch: CommunityDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<CommunityDocument>> {
        val combinedQuery = buildQuery(primaryKeyFetch, communityDocumentSearch)
        val reverse = when {
            primaryKeyFetch == null || primaryKeyFetch.cursor == null -> true
            primaryKeyFetch.cursor is Cursor.DescCursor<PrimaryKey> -> true
            else -> false
        }
        val sortById = Sort(SortField("id2", SortField.Type.LONG, reverse))
        Napier.i {
            "lucene search query $combinedQuery $sortById $reverse"
        }
        return useLucene {
            searchDocumentList(combinedQuery, primaryKeyFetch, sortById, LuceneCommunityDocument)
        }
    }

    private fun buildQuery(
        primaryKeyFetch: PrimaryKeyFetch?,
        communityDocumentSearch: CommunityDocumentSearch
    ): Query {
        return buildPrimaryKeyLuceneSearchQuery(primaryKeyFetch) {
            when (communityDocumentSearch) {
                is CommunityDocumentSearch.Keyword -> {
                    preprocessUserInputKeyword(communityDocumentSearch.keyword)?.let {
                        add(BooleanQuery.Builder().apply {
                            add(
                                MultiFieldQueryParser(
                                    arrayOf("name"),
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

class LuceneCommunitySearchServiceFactory : CommunitySearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): CommunitySearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneCommunitySearchService(path, isInMemory)
        }
    }
}
