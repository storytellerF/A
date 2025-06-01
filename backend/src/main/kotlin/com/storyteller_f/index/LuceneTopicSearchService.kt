package com.storyteller_f.index

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import com.storyteller_f.types.Cursor
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PrimaryKeyFetch
import io.github.aakira.napier.Napier
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class LuceneTopicSearchService(private val path: Path, private val isInMemory: Boolean = false) : TopicSearchService {
    init {
        Napier.i {
            "lucene path $path"
        }
        if (!path.exists()) {
            path.createDirectories()
        }
    }

    private val analyzer = StandardAnalyzer()

    override suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit> {
        return useLucene {
            IndexWriter(it, IndexWriterConfig(analyzer)).use { writer ->
                val seq = writer.addDocuments(topics.map { document ->
                    document.saveAsDocument()
                })
                Napier.d {
                    "lucene save document $seq"
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
        word: List<String>?,
        documentSearch: DocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<TopicDocument>> {
        return useLucene {
            try {
                DirectoryReader.open(it).use { reader ->
                    val searcher = IndexSearcher(reader)
                    val combinedQuery = buildQuery(primaryKeyFetch, word, documentSearch)
                    val reverse = when {
                        primaryKeyFetch == null -> true
                        primaryKeyFetch.cursor is Cursor.NextCursor<*> -> true
                        else -> true
                    }
                    val sortById = Sort(SortField("id2", SortField.Type.LONG, reverse))
                    Napier.i {
                        "lucene search query $combinedQuery $sortById $reverse"
                    }
                    val docs = searcher.search(combinedQuery, primaryKeyFetch?.size ?: 0, sortById)
                    val scoreDocs = docs.scoreDocs
                    PaginationResult(scoreDocs.mapNotNull { doc ->
                        searcher.storedFields().document(doc.doc)?.let { document ->
                            val id = document.get("id1").toPrimaryKeyOrNull()
                            if (id != null) {
                                restoreDocument(id, document)
                            } else {
                                null
                            }
                        }
                    }, docs.totalHits.value)
                }
            } catch (_: IndexNotFoundException) {
                PaginationResult(emptyList(), 0)
            }
        }
    }

    private fun BooleanQuery.Builder.addPagingQuery(fetch: PrimaryKeyFetch?) {
        when {
            fetch == null -> {}
            fetch.cursor is Cursor.PreCursor<*> -> {
                if (fetch.cursor.value is PrimaryKey) {
                    val preTopicId = fetch.cursor.value + 1
                    add(
                        LongPoint.newRangeQuery("id1", preTopicId, Long.MAX_VALUE),
                        BooleanClause.Occur.MUST
                    )
                }
            }

            fetch.cursor is Cursor.NextCursor<*> -> {
                if (fetch.cursor.value is PrimaryKey) {
                    val nextTopicId = fetch.cursor.value - 1
                    add(
                        LongPoint.newRangeQuery("id1", Long.MIN_VALUE, nextTopicId),
                        BooleanClause.Occur.MUST
                    )
                }
            }

            else -> {}
        }
    }

    private fun buildQuery(
        fetch: PrimaryKeyFetch?,
        word: List<String>?,
        documentSearch: DocumentSearch
    ): Query? {
        val analyzer = StandardAnalyzer()
        val combinedQuery = BooleanQuery.Builder()
        combinedQuery.addPagingQuery(fetch)
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
        when (documentSearch) {
            is DocumentSearch.Recommend -> {
                combinedQuery.add(
                    LongPoint.newSetQuery("parentId", documentSearch.communities),
                    BooleanClause.Occur.MUST
                )
                combinedQuery.add(LongPoint.newExactQuery("author", documentSearch.uid), BooleanClause.Occur.MUST_NOT)
            }

            DocumentSearch.RecommendNotLogin, DocumentSearch.CommunityRoot -> {
                combinedQuery.add(TermQuery(Term("parentType", ObjectType.COMMUNITY.name)), BooleanClause.Occur.MUST)
            }

            is DocumentSearch.Topics -> {
                combinedQuery.add(
                    LongPoint.newExactQuery("parentId", documentSearch.parentId),
                    BooleanClause.Occur.MUST
                )
            }

            DocumentSearch.All -> {}
        }
        val build = combinedQuery.build()
        return if (build.clauses().size == 1) {
            build.clauses().first().query
        } else {
            build
        }
    }

    private fun <R> useLucene(block: (FSDirectory) -> R): Result<R> {
        return runCatching {
            if (isInMemory) {
                NIOFSDirectory(path).use(block)
            } else {
                FSDirectory.open(path).use(block)
            }
        }
    }
}

private fun restoreDocument(
    id: PrimaryKey,
    document: Document
): TopicDocument = TopicDocument(
    id,
    document.get("content"),
    rootId = document.get("rootId").toPrimaryKey(),
    rootType = document.get("rootType"),
    parentId = document.get("parentId").toPrimaryKey(),
    parentType = document.get("parentType"),
    author = document.get("author").toPrimaryKey()
)

private fun TopicDocument.saveAsDocument(): Document {
    val doc = Document()
    doc.add(LongField("id1", id, Field.Store.YES))
    doc.add(NumericDocValuesField("id2", id))
    doc.add(TextField("content", content, Field.Store.YES))
    doc.add(LongField("rootId", rootId, Field.Store.YES))
    doc.add(LongField("parentId", parentId, Field.Store.YES))
    doc.add(StringField("rootType", rootType, Field.Store.YES))
    doc.add(StringField("parentType", parentType, Field.Store.YES))
    doc.add(LongField("author", author, Field.Store.YES))
    return doc
}
