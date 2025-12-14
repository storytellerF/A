package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchServiceFactory
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import io.github.aakira.napier.Napier
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.TermQuery
import java.nio.file.Path

data class LuceneTopicDocument(
    val topicDocument: TopicDocument
) : LuceneDocument {

    override fun save(): Document {
        val id = topicDocument.id
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("content", topicDocument.content, Field.Store.YES))
            add(LongField("rootId", topicDocument.rootId, Field.Store.YES))
            add(LongField("parentId", topicDocument.parentId, Field.Store.YES))
            add(StringField("rootType", topicDocument.rootType, Field.Store.YES))
            add(StringField("parentType", topicDocument.parentType, Field.Store.YES))
            add(LongField("author", topicDocument.author, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<TopicDocument> {

        override fun restore(
            id: PrimaryKey,
            document: Document
        ): TopicDocument {
            return TopicDocument(
                id,
                document.get("content"),
                rootId = document.get("rootId").toPrimaryKey(),
                rootType = document.get("rootType"),
                parentId = document.get("parentId").toPrimaryKey(),
                parentType = document.get("parentType"),
                author = document.get("author").toPrimaryKey()
            )
        }
    }
}

class LuceneTopicSearchService(path: Path, isInMemory: Boolean = false) :
    Lucene(path, isInMemory), TopicSearchService {

    override suspend fun saveDocument(documents: List<TopicDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents.map {
                LuceneTopicDocument(it)
            }, analyzer)
        }
    }

    override suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        if (idList.isEmpty()) return Result.success(emptyList())
        return useLucene {
            try {
                DirectoryReader.open(this).use { reader ->
                    if (idList.size == 1) {
                        val searcher = IndexSearcher(reader)
                        val id = idList.first()
                        val topDocs = searcher.search(LongPoint.newExactQuery("id1", id), 1)
                        listOf(topDocs.scoreDocs.firstOrNull()?.let { scoreDoc ->
                            val document = searcher.storedFields().document(scoreDoc.doc)
                            LuceneTopicDocument.restore(id, document)
                        })
                    } else {
                        val searcher = IndexSearcher(reader)
                        val query = LongPoint.newSetQuery("id1", idList)
                        val topDocs = searcher.search(query, idList.size)
                        val map = topDocs.scoreDocs.map { scoreDoc ->
                            val document = searcher.storedFields().document(scoreDoc.doc)
                            LuceneTopicDocument.restore(document.get("id1").toPrimaryKey(), document)
                        }.associateBy { document -> document.id }
                        idList.map { id ->
                            map[id]
                        }
                    }
                }
            } catch (_: IndexNotFoundException) {
                emptyList()
            }
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            cleanAll(analyzer)
        }
    }

    override suspend fun searchDocument(
        topicDocumentSearch: TopicDocumentSearch
    ): Result<PaginationResult<TopicDocument>> {
        if (topicDocumentSearch is TopicDocumentSearch.AllCommunityRoot && topicDocumentSearch.word.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        if (topicDocumentSearch is TopicDocumentSearch.Topics && topicDocumentSearch.word.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        if (topicDocumentSearch is TopicDocumentSearch.All && topicDocumentSearch.word.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }

        val combinedQuery = buildQuery(topicDocumentSearch)
        Napier.i {
            "lucene search query $combinedQuery"
        }
        return useLucene {
            val (fetch, sort) = when (topicDocumentSearch) {
                is TopicDocumentSearch.Recommend -> topicDocumentSearch.fetch to Sort(
                    SortField("id2", SortField.Type.LONG, true)
                )

                is TopicDocumentSearch.RecommendNotLogin -> topicDocumentSearch.fetch to Sort(
                    SortField("id2", SortField.Type.LONG, true)
                )

                is TopicDocumentSearch.AllCommunityRoot -> {
                    topicDocumentSearch.fetch to Sort.RELEVANCE
                }

                is TopicDocumentSearch.Topics -> {
                    topicDocumentSearch.fetch to Sort.RELEVANCE
                }

                is TopicDocumentSearch.All -> {
                    topicDocumentSearch.fetch to Sort.RELEVANCE
                }
            }
            searchDocumentList(combinedQuery, fetch, sort, LuceneTopicDocument)
        }
    }

    private fun buildQuery(
        topicDocumentSearch: TopicDocumentSearch,
    ): Query {
        return BooleanQuery.Builder().apply {
            when (topicDocumentSearch) {
                is TopicDocumentSearch.Recommend -> {
                    addParentIdListQuery(topicDocumentSearch)
                    addLongQuery("author", topicDocumentSearch.uid)
                }

                is TopicDocumentSearch.RecommendNotLogin -> {
                    addParentTypeQuery()
                }

                is TopicDocumentSearch.AllCommunityRoot -> {
                    addParentTypeQuery()
                    addMatchQuery(analyzer, topicDocumentSearch.word, "content")
                }

                is TopicDocumentSearch.Topics -> {
                    addMustLongQuery("parentId", topicDocumentSearch.parentId)
                    addMatchQuery(analyzer, topicDocumentSearch.word, "content")
                }

                is TopicDocumentSearch.All -> {
                    addMatchQuery(analyzer, topicDocumentSearch.word, "content")
                }
            }
        }.build()
    }

    private fun BooleanQuery.Builder.addParentIdListQuery(topicDocumentSearch: TopicDocumentSearch.Recommend) {
        add(LongPoint.newSetQuery("parentId", topicDocumentSearch.communities), BooleanClause.Occur.MUST)
    }

    private fun BooleanQuery.Builder.addLongQuery(
        fieldName: String,
        termValue: PrimaryKey
    ) {
        add(LongPoint.newExactQuery(fieldName, termValue), BooleanClause.Occur.MUST_NOT)
    }

    private fun BooleanQuery.Builder.addMustLongQuery(
        fieldName: String,
        termValue: PrimaryKey
    ) {
        add(LongPoint.newExactQuery(fieldName, termValue), BooleanClause.Occur.MUST)
    }

    private fun BooleanQuery.Builder.addParentTypeQuery() {
        add(TermQuery(Term("parentType", ObjectType.COMMUNITY.name)), BooleanClause.Occur.MUST)
    }
}

class LuceneTopicSearchServiceFactory : TopicSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): TopicSearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneTopicSearchService(path, isInMemory)
        }
    }
}
