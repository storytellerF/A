package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
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
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
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
        userDocumentSearch: UserDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<UserDocument>> {
        val combinedQuery = buildQuery(primaryKeyFetch, userDocumentSearch)
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
            searchDocumentList(combinedQuery, primaryKeyFetch, sortById, LuceneUserDocument)
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
                                MultiFieldQueryParser(arrayOf("nickname"), analyzer).parse(it),
                                BooleanClause.Occur.SHOULD
                            )
                            add(
                                MultiFieldQueryParser(arrayOf("aid"), analyzer).parse(it),
                                BooleanClause.Occur.SHOULD
                            )
                        }.build(), BooleanClause.Occur.MUST)
                    }
                }
            }
        }
    }
}

class LuceneUserSearchServiceFactory : UserSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): UserSearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneUserSearchService(path, isInMemory)
        }
    }
}
