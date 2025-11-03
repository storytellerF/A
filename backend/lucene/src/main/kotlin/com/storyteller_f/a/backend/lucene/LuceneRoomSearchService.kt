package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.RoomSearchServiceFactory
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

data class LuceneRoomDocument(val roomDocument: RoomDocument) :
    LuceneDocument {
    override fun save(): Document {
        val id = roomDocument.id
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("name", roomDocument.name, Field.Store.YES))
            add(TextField("aid", roomDocument.aid, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<RoomDocument> {
        override fun restore(
            id: PrimaryKey,
            document: Document
        ): RoomDocument {
            return RoomDocument(id, document.get("name"), document.get("aid"))
        }
    }
}

class LuceneRoomSearchService(path: Path, isInMemory: Boolean = false) : Lucene(path, isInMemory),
    RoomSearchService {
    override suspend fun saveDocument(documents: List<RoomDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents.map {
                LuceneRoomDocument(it)
            }, analyzer)
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            cleanAll(analyzer)
        }
    }

    override suspend fun searchDocument(
        roomDocumentSearch: RoomDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<RoomDocument>> {
        val combinedQuery = buildQuery(primaryKeyFetch, roomDocumentSearch)
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
            searchDocumentList(combinedQuery, primaryKeyFetch, sortById, LuceneRoomDocument)
        }
    }

    private fun buildQuery(
        primaryKeyFetch: PrimaryKeyFetch?,
        roomDocumentSearch: RoomDocumentSearch
    ): Query {
        return buildPrimaryKeyLuceneSearchQuery(primaryKeyFetch) {
            when (roomDocumentSearch) {
                is RoomDocumentSearch.Keyword -> {
                    preprocessUserInputKeyword(roomDocumentSearch.words)?.let {
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

class LuceneRoomSearchServiceFactory : RoomSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): RoomSearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneRoomSearchService(path, isInMemory)
        }
    }
}
