package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.service.UserDocumentSearch
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.core.service.UserSearchServiceFactory
import com.storyteller_f.shared.type.PrimaryKey
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

data class LuceneUserDocument(val userDocument: UserDocument) :
    LuceneDocument {
    override fun save(): Document {
        val id = userDocument.id
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("nickname", userDocument.nickname, Field.Store.YES))
            add(TextField("aid", userDocument.aid, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<UserDocument> {
        override fun restore(
            id: PrimaryKey,
            document: Document
        ): UserDocument {
            return UserDocument(id, document.get("nickname"), document.get("aid"))
        }
    }
}

class LuceneUserSearchService(path: Path, isInMemory: Boolean = false) : Lucene(path, isInMemory),
    UserSearchService {
    override suspend fun saveDocument(documents: List<UserDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents.map {
                LuceneUserDocument(it)
            }, analyzer)
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            cleanAll(analyzer)
        }
    }

    override suspend fun searchDocument(
        userDocumentSearch: UserDocumentSearch
    ): Result<PaginationResult<UserDocument>> {
        if (userDocumentSearch is UserDocumentSearch.Keyword && userDocumentSearch.word.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val combinedQuery = buildQuery(userDocumentSearch)
        Napier.i {
            "lucene search query $combinedQuery"
        }
        return useLucene {
            when (userDocumentSearch) {
                is UserDocumentSearch.Keyword -> {
                    searchDocumentList(
                        combinedQuery,
                        userDocumentSearch.fetch,
                        Sort.RELEVANCE,
                        LuceneUserDocument
                    )
                }
            }
        }
    }

    private fun buildQuery(
        userDocumentSearch: UserDocumentSearch
    ): Query {
        return BooleanQuery.Builder().apply {
            when (userDocumentSearch) {
                is UserDocumentSearch.Keyword -> {
                    addPrioritizedFieldsQuery(userDocumentSearch.word, "aid", "nickname")
                }
            }
        }.build()
    }
}

class LuceneUserSearchServiceFactory : UserSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): UserSearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneUserSearchService(path.resolve("user"), isInMemory)
        }
    }
}
