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
                val addDocuments = writer.addDocuments(
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
                    "save document $addDocuments in `lucene`"
                }
            }
        }
    }

    override suspend fun getDocument(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        if (idList.isEmpty()) return Result.success(emptyList())
        return useLucene {
            try {
                DirectoryReader.open(it).use { reader ->
                    val searcher = IndexSearcher(reader)
                    idList.map { id ->
                        val topDocs = searcher.search(LongPoint.newExactQuery("id1", id.toLong()), 1)
                        topDocs.scoreDocs.firstOrNull()?.let { scoreDoc ->
                            val document = searcher.storedFields().document(scoreDoc.doc)
                            restoreDocument(id, document)
                        }
                    }
                }
            } catch (_: IndexNotFoundException) {
                List(idList.size) {
                    null
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
        size: Int,
        nextTopicId: PrimaryKey?,
        author: PrimaryKey?,
        root: Pair<PrimaryKey, ObjectType>?,
        parent: Pair<PrimaryKey, ObjectType>?
    ): Result<PaginationResult<TopicDocument>> {
        return useLucene {
            DirectoryReader.open(it).use { reader ->
                val searcher = IndexSearcher(reader)
                val analyzer = StandardAnalyzer()
                val combinedQuery = BooleanQuery
                    .Builder()
                    .add(
                        LongPoint.newRangeQuery("id1", Long.MIN_VALUE, nextTopicId?.minus(1) ?: Long.MAX_VALUE),
                        BooleanClause.Occur.MUST
                    )
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
                            ).parse(filtered.joinToString<String>(" ")),
                            BooleanClause.Occur.MUST
                        )
                    }
                }
                root?.let {
                    combinedQuery.add(LongPoint.newExactQuery("rootId", it.first), BooleanClause.Occur.MUST)
                        .add(TermQuery(Term("rootType", it.second.name)), BooleanClause.Occur.MUST)
                }
                parent?.let {
                    combinedQuery.add(LongPoint.newExactQuery("parentId", it.first), BooleanClause.Occur.MUST)
                        .add(TermQuery(Term("parentType", it.second.name)), BooleanClause.Occur.MUST)
                }
                val sortById = Sort(SortField("id2", SortField.Type.LONG, true))
                val docs = searcher.search(combinedQuery.build(), size, sortById)
                val scoreDocs = docs.scoreDocs
                PaginationResult(scoreDocs.mapNotNull {
                    searcher.storedFields().document(it.doc).let {
                        val content = it.get("content")
                        val id = it.get("id1").toPrimaryKeyOrNull()
                        if (content != null && id != null) {
                            restoreDocument(id, it)
                        } else {
                            null
                        }
                    }
                }, docs.totalHits.value)
            }
        }
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
