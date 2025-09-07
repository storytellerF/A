package com.storyteller_f.a.backend.service.search.lucene

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.service.search.Lucene
import com.storyteller_f.a.backend.service.search.TopicDocument
import com.storyteller_f.a.backend.service.search.TopicDocumentSearch
import com.storyteller_f.a.backend.service.search.TopicSearchService
import com.storyteller_f.a.backend.service.search.addMatchQuery
import com.storyteller_f.a.backend.service.search.buildPrimaryKeyLuceneSearchQuery
import com.storyteller_f.a.backend.service.search.cleanAll
import com.storyteller_f.a.backend.service.search.saveDocumentList
import com.storyteller_f.a.backend.service.search.searchDocumentList
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import io.github.aakira.napier.Napier
import org.apache.lucene.document.LongPoint
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

class LuceneTopicSearchService(path: Path, isInMemory: Boolean = false) :
    Lucene(path, isInMemory), TopicSearchService {

    override suspend fun saveDocument(documents: List<TopicDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents, analyzer)
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
                            TopicDocument.restore(id, document)
                        })
                    } else {
                        val searcher = IndexSearcher(reader)
                        val query = LongPoint.newSetQuery("id1", idList)
                        val topDocs = searcher.search(query, idList.size)
                        val map = topDocs.scoreDocs.map { scoreDoc ->
                            val document = searcher.storedFields().document(scoreDoc.doc)
                            TopicDocument.restore(document.get("id1").toPrimaryKey(), document)
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
        topicDocumentSearch: TopicDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?,
    ): Result<PaginationResult<TopicDocument>> {
        val combinedQuery = buildQuery(primaryKeyFetch, topicDocumentSearch)
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
            searchDocumentList(combinedQuery, primaryKeyFetch, sortById, TopicDocument)
        }
    }

    private fun buildQuery(
        fetch: PrimaryKeyFetch?,
        topicDocumentSearch: TopicDocumentSearch,
    ): Query? {
        return buildPrimaryKeyLuceneSearchQuery(fetch) {
            when (topicDocumentSearch) {
                is TopicDocumentSearch.Recommend -> {
                    addParentIdListQuery(topicDocumentSearch)
                    addLongQuery("author", topicDocumentSearch.uid)
                }

                TopicDocumentSearch.RecommendNotLogin -> {
                    addParentTypeQuery()
                }

                is TopicDocumentSearch.CommunityRoot -> {
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
        }
    }

    private fun BooleanQuery.Builder.addParentIdListQuery(topicDocumentSearch: TopicDocumentSearch.Recommend) {
        add(
            LongPoint.newSetQuery("parentId", topicDocumentSearch.communities),
            BooleanClause.Occur.MUST
        )
    }

    private fun BooleanQuery.Builder.addLongQuery(
        fieldName: String,
        termValue: PrimaryKey
    ) {
        add(
            LongPoint.newExactQuery(fieldName, termValue),
            BooleanClause.Occur.MUST_NOT
        )
    }

    private fun BooleanQuery.Builder.addMustLongQuery(
        fieldName: String,
        termValue: PrimaryKey
    ) {
        add(
            LongPoint.newExactQuery(fieldName, termValue),
            BooleanClause.Occur.MUST
        )
    }

    private fun BooleanQuery.Builder.addParentTypeQuery() {
        add(
            TermQuery(Term("parentType", ObjectType.COMMUNITY.name)),
            BooleanClause.Occur.MUST
        )
    }
}
