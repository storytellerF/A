package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
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
            // 添加communityId字段（如果存在）
            roomDocument.communityId?.let { communityId ->
                add(LongField("communityId", communityId, Field.Store.YES))
            }
        }
    }

    companion object : LuceneDocumentCompanion<RoomDocument> {
        override fun restore(
            id: PrimaryKey,
            document: Document
        ): RoomDocument {
            // 尝试从文档中获取communityId，如果不存在则为null

            return RoomDocument(id, document.get("name"), document.get("aid"), document.get("communityId")?.toLong())
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
        roomDocumentSearch: RoomDocumentSearch
    ): Result<PaginationResult<RoomDocument>> {
        if (roomDocumentSearch is RoomDocumentSearch.Keyword && roomDocumentSearch.words.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val combinedQuery = buildQuery(roomDocumentSearch)
        Napier.i {
            "lucene search query $combinedQuery"
        }
        return useLucene {
            when (roomDocumentSearch) {
                is RoomDocumentSearch.Keyword -> {
                    searchDocumentList(
                        combinedQuery,
                        roomDocumentSearch.fetch,
                        Sort.RELEVANCE,
                        LuceneRoomDocument
                    )
                }
            }
        }
    }

    private fun buildQuery(
        roomDocumentSearch: RoomDocumentSearch
    ): Query {
        return BooleanQuery.Builder().apply {
            when (roomDocumentSearch) {
                is RoomDocumentSearch.Keyword -> {
                    val keyword = preprocessUserInputKeyword(roomDocumentSearch.words)
                    add(BooleanQuery.Builder().apply {
                        add(MultiFieldQueryParser(arrayOf("name"), analyzer).parse(keyword), BooleanClause.Occur.SHOULD)
                        add(MultiFieldQueryParser(arrayOf("aid"), analyzer).parse(keyword), BooleanClause.Occur.SHOULD)
                    }.build(), BooleanClause.Occur.MUST)
                    // 添加对communityId的过滤
                    roomDocumentSearch.communityId?.let { communityId ->
                        add(LongField.newExactQuery("communityId", communityId), BooleanClause.Occur.MUST)
                    }
                }
            }
        }.build()
    }
}

class LuceneRoomSearchServiceFactory : RoomSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): RoomSearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneRoomSearchService(path.resolve("room"), isInMemory)
        }
    }
}
