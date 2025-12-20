package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.FileSearchService
import com.storyteller_f.a.backend.core.service.FileSearchServiceFactory
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StringField
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import java.nio.file.Path

data class LuceneFileDocument(val fileDocument: FileDocument) : LuceneDocument {
    override fun save(): Document {
        val id = fileDocument.id
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(StringField("name", fileDocument.name.lowercase(), Field.Store.YES))
            add(LongField("ownerId", fileDocument.ownerId, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<FileDocument> {
        override fun restore(
            id: PrimaryKey,
            document: Document
        ): FileDocument {
            return FileDocument(id, document.get("name"), document.get("ownerId").toLong())
        }
    }
}

class LuceneFileSearchService(path: Path, isInMemory: Boolean = false) : Lucene(path, isInMemory),
    FileSearchService {
    override suspend fun saveDocument(documents: List<FileDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents.map {
                LuceneFileDocument(it)
            }, analyzer)
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            cleanAll(analyzer)
        }
    }

    override suspend fun searchDocument(
        fileDocumentSearch: FileDocumentSearch
    ): Result<PaginationResult<FileDocument>> {
        if (fileDocumentSearch is FileDocumentSearch.Keyword && fileDocumentSearch.word.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val combinedQuery = buildQuery(fileDocumentSearch)
        Napier.i {
            "lucene search file query $combinedQuery"
        }
        return useLucene {
            when (fileDocumentSearch) {
                is FileDocumentSearch.Keyword -> {
                    searchDocumentList(
                        combinedQuery,
                        fileDocumentSearch.fetch,
                        Sort.RELEVANCE,
                        LuceneFileDocument
                    )
                }
            }
        }
    }

    private fun buildQuery(
        fileDocumentSearch: FileDocumentSearch
    ): Query {
        return BooleanQuery.Builder().apply {
            when (fileDocumentSearch) {
                is FileDocumentSearch.Keyword -> {
                    // 添加 ownerId 过滤
                    fileDocumentSearch.ownerId?.let { owner ->
                        add(LongField.newExactQuery("ownerId", owner), BooleanClause.Occur.MUST)
                    }
                    // 添加关键词搜索
                    addPrefixAndInclusionQuery(fileDocumentSearch.word, "name")
                }
            }
        }.build()
    }
}

class LuceneFileSearchServiceFactory : FileSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): FileSearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneFileSearchService(path.resolve("file"), isInMemory)
        }
    }
}
