package com.storyteller_f.index

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import com.storyteller_f.types.PaginationResult
import io.github.aakira.napier.Napier
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

class LuceneTopicSearchService(private val path: Path) : TopicSearchService {
    private val analyzer = StandardAnalyzer()

    override suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit> {
        return useLucene {
            IndexWriter(it, IndexWriterConfig(analyzer)).use { writer ->
                val seqNo = writer.addDocuments(
                    topics.map { document ->
                        val doc = Document()
                        doc.add(LongField("id1", document.id, Field.Store.YES))
                        doc.add(NumericDocValuesField("id2", document.id))
                        doc.add(TextField("content", document.content, Field.Store.YES))
                        doc.add(LongField("rootId", document.rootId, Field.Store.YES))
                        doc.add(LongField("parentId", document.parentId, Field.Store.YES))
                        doc.add(StringField("rootType", document.rootType, Field.Store.YES))
                        doc.add(StringField("parentType", document.parentType, Field.Store.YES))
                        doc.add(LongField("author", document.author, Field.Store.YES))
                        doc
                    }
                )
                Napier.d {
                    "save document $seqNo in `lucene`"
                }
            }
        }
    }

    override suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        if (idList.isEmpty()) return Result.success(emptyList())
        if (idList.size == 1) {
            return useLucene {
                try {
                    DirectoryReader.open(it).use { reader ->
                        val searcher = IndexSearcher(reader)
                        val id = idList.first()
                        val topDocs = searcher.search(LongPoint.newExactQuery("id1", id), 1)
                        listOf(topDocs.scoreDocs.firstOrNull()?.let { scoreDoc ->
                            val document = searcher.storedFields().document(scoreDoc.doc)
                            restoreDocument(id, document)
                        })
                    }
                } catch (_: IndexNotFoundException) {
                    List(idList.size) {
                        null
                    }
                }
            }
        } else {
            return useLucene {
                try {
                    DirectoryReader.open(it).use { reader ->
                        val searcher = IndexSearcher(reader)
                        val query = LongPoint.newSetQuery("id1", idList)
                        val topDocs = searcher.search(query, idList.size)
                        val map = topDocs.scoreDocs.map { scoreDoc ->
                            val document = searcher.storedFields().document(scoreDoc.doc)
                            restoreDocument(document.get("id1").toPrimaryKey(), document)
                        }.associateBy { document -> document.id }
                        idList.map { id ->
                            map[id]
                        }
                    }
                } catch (_: IndexNotFoundException) {
                    List(idList.size) {
                        null
                    }
                }
            }
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            IndexWriter(it, IndexWriterConfig(analyzer)).use { writer ->
                writer.deleteDocuments(MatchAllDocsQuery())
            }
        }
    }

    override suspend fun searchDocument(
        size: Int,
        word: List<String>?,
        preTopicId: PrimaryKey?,
        nextTopicId: PrimaryKey?,
        author: PrimaryKey?,
        root: Pair<PrimaryKey?, ObjectType>?,
        parent: Pair<PrimaryKey?, ObjectType>?
    ): Result<PaginationResult<TopicDocument>> {
        return useLucene {
            try {
                DirectoryReader.open(it).use { reader ->
                    val searcher = IndexSearcher(reader)
                    val combinedQuery = buildQuery(preTopicId, nextTopicId, word, root, parent).build()
                    Napier.i {
                        "lucene search query $combinedQuery"
                    }
                    val sortById = Sort(SortField("id2", SortField.Type.LONG, preTopicId == null))
                    val docs = searcher.search(combinedQuery, size, sortById)
                    val scoreDocs = docs.scoreDocs
                    PaginationResult(scoreDocs.mapNotNull { doc ->
                        searcher.storedFields().document(doc.doc)?.let { document ->
                            val content = document.get("content")
                            val id = document.get("id1").toPrimaryKeyOrNull()
                            if (content != null && id != null) {
                                restoreDocument(id, document)
                            } else {
                                null
                            }
                        }
                    }, docs.totalHits.value)
                }
            } catch (e: IndexNotFoundException) {
                PaginationResult(emptyList(), 0)
            }
        }
    }

    private fun buildQuery(
        preTopicId: PrimaryKey?,
        nextTopicId: PrimaryKey?,
        word: List<String>?,
        root: Pair<PrimaryKey?, ObjectType>?,
        parent: Pair<PrimaryKey?, ObjectType>?
    ): BooleanQuery.Builder {
        val analyzer = StandardAnalyzer()
        val combinedQuery = BooleanQuery
            .Builder()
        if (nextTopicId != null) {
            combinedQuery.add(
                LongPoint.newRangeQuery("id1", Long.MIN_VALUE, nextTopicId.minus(1)),
                BooleanClause.Occur.MUST
            )
        } else if (preTopicId != null) {
            combinedQuery.add(
                LongPoint.newRangeQuery("id1", Long.MIN_VALUE, preTopicId.minus(1)),
                BooleanClause.Occur.MUST
            )
        }
        word?.let {
            val filtered = it.map { string ->
                string.trim()
            }.filter { w ->
                w.isNotBlank()
            }
            if (filtered.isNotEmpty()) {
                combinedQuery.add(
                    MultiFieldQueryParser(
                        arrayOf("content"),
                        analyzer
                    ).parse(filtered.joinToString(" ")),
                    BooleanClause.Occur.MUST
                )
            }
        }
        root?.let {
            it.first?.let { rootId ->
                combinedQuery.add(LongPoint.newExactQuery("rootId", rootId), BooleanClause.Occur.MUST)
            }
            combinedQuery.add(TermQuery(Term("rootType", it.second.name)), BooleanClause.Occur.MUST)
        }
        parent?.let {
            it.first?.let { parentId ->
                combinedQuery.add(LongPoint.newExactQuery("parentId", parentId), BooleanClause.Occur.MUST)
            }
            combinedQuery.add(TermQuery(Term("parentType", it.second.name)), BooleanClause.Occur.MUST)
        }
        return combinedQuery
    }

    private fun <R> useLucene(block: (FSDirectory) -> R): Result<R> {
        return runCatching {
            FSDirectory.open(path).use(block)
        }
    }
}

private fun restoreDocument(
    id: PrimaryKey,
    document: Document
): TopicDocument = TopicDocument(
    id,
    document.get("content"),
    document.get("rootId").toPrimaryKey(),
    document.get("rootType"),
    document.get("parentId").toPrimaryKey(),
    document.get("parentType"),
    document.get("author").toPrimaryKey()
)
