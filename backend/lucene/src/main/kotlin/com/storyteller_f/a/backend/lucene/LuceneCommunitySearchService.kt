package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
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
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
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
        communityDocumentSearch: CommunityDocumentSearch
    ): Result<PaginationResult<CommunityDocument>> {
        if (communityDocumentSearch is CommunityDocumentSearch.Keyword && communityDocumentSearch.keyword.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val combinedQuery = buildQuery(communityDocumentSearch)
        Napier.i {
            "lucene search query $combinedQuery"
        }
        return useLucene {
            when (communityDocumentSearch) {
                is CommunityDocumentSearch.Keyword -> {
                    searchDocumentList(
                        combinedQuery,
                        communityDocumentSearch.fetch,
                        Sort.RELEVANCE,
                        LuceneCommunityDocument
                    )
                }
            }
        }
    }

    private fun buildQuery(
        communityDocumentSearch: CommunityDocumentSearch
    ): Query {
        return BooleanQuery.Builder().apply {
            when (communityDocumentSearch) {
                is CommunityDocumentSearch.Keyword -> {
                    addPrioritizedFieldsQuery(communityDocumentSearch.keyword, "aid", "name")
                }
            }
        }.build()
    }
}

class LuceneCommunitySearchServiceFactory : CommunitySearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): CommunitySearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneCommunitySearchService(path.resolve("community"), isInMemory)
        }
    }
}
