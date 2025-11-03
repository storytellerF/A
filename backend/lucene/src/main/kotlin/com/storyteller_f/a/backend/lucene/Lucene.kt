package com.storyteller_f.a.backend.lucene

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.LongPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.text.isNullOrBlank

abstract class Lucene(private val path: Path, private val isInMemory: Boolean = false) {
    init {
        Napier.i {
            "lucene path $path"
        }
        if (!path.exists()) {
            path.createDirectories()
        }
    }

    val analyzer = StandardAnalyzer()

    suspend fun <R> useLucene(block: FSDirectory.() -> R) =
        runCatching {
            withContext(Dispatchers.IO) {
                if (isInMemory) {
                    NIOFSDirectory(path).use(block)
                } else {
                    FSDirectory.open(path).use(block)
                }
            }
        }
}

fun FSDirectory.cleanAll(
    analyzer: StandardAnalyzer
) {
    IndexWriter(this, IndexWriterConfig(analyzer)).use { writer ->
        writer.deleteDocuments(MatchAllDocsQuery())
    }
}

fun <T : LuceneDocument> FSDirectory.saveDocumentList(
    documents: List<T>,
    standardAnalyzer: StandardAnalyzer
) {
    IndexWriter(this, IndexWriterConfig(standardAnalyzer)).use { writer ->
        val seq = writer.addDocuments(documents.map { document ->
            document.save()
        })
        Napier.d {
            "lucene save document $seq"
        }
    }
}

fun <D, T : LuceneDocumentCompanion<D>> FSDirectory.searchDocumentList(
    combinedQuery: Query?,
    primaryKeyFetch: PrimaryKeyFetch?,
    sortById: Sort,
    t: T
): PaginationResult<D> {
    return try {
        DirectoryReader.open(this).use { reader ->
            val searcher = IndexSearcher(reader)
            val docs = searcher.search(combinedQuery, primaryKeyFetch?.size ?: 0, sortById)
            val scoreDocs = docs.scoreDocs
            PaginationResult(scoreDocs.mapNotNull { doc ->
                searcher.storedFields().document(doc.doc)?.let { document ->
                    val id = document.get("id1").toPrimaryKeyOrNull()
                    if (id != null) {
                        t.restore(id, document)
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

fun BooleanQuery.Builder.addPagingQuery(fetch: PrimaryKeyFetch?) {
    when {
        fetch == null -> {}
        fetch.cursor is Cursor.AscCursor<PrimaryKey> -> {
            val cursor = fetch.cursor as Cursor.AscCursor<PrimaryKey>
            val preTopicId = cursor.value + 1
            add(
                LongPoint.newRangeQuery("id1", preTopicId, Long.MAX_VALUE),
                BooleanClause.Occur.MUST
            )
        }

        fetch.cursor is Cursor.DescCursor<PrimaryKey> -> {
            val cursor = fetch.cursor as Cursor.DescCursor<PrimaryKey>
            val nextTopicId = cursor.value - 1
            add(
                LongPoint.newRangeQuery("id1", Long.MIN_VALUE, nextTopicId),
                BooleanClause.Occur.MUST
            )
        }

        else -> {}
    }
}

fun BooleanQuery.Builder.addMatchQuery(
    analyzer: StandardAnalyzer,
    words: List<String>?,
    fieldName: String
) {
    preprocessUserInputKeyword(words)?.let {
        add(
            MultiFieldQueryParser(
                arrayOf(fieldName),
                analyzer
            ).parse(it),
            BooleanClause.Occur.MUST
        )
    }
}

fun buildPrimaryKeyLuceneSearchQuery(
    fetch: PrimaryKeyFetch?,
    block: BooleanQuery.Builder.() -> Unit
): Query {
    val combinedQuery = BooleanQuery.Builder()
    combinedQuery.addPagingQuery(fetch)
    combinedQuery.block()
    val build = combinedQuery.build()
    return if (build.clauses().size == 1) {
        build.clauses().first().query
    } else {
        build
    }
}

interface LuceneDocument {
    fun save(): Document
}

interface LuceneDocumentCompanion<T> {
    fun restore(id: PrimaryKey, document: Document): T
}

fun<T> buildLuceneSearchService(env: MergedEnv, b: (Path, Boolean) -> T): T {
    val luceneBase = env["LUCENE_BASE_PATH"]
    val (path, isInMemory) = if (luceneBase.isNullOrBlank()) {
        Napier.i {
            "use in-memory document service"
        }
        MemoryFileSystemBuilder.newLinux().build().getPath("/documents") to true
    } else {
        val p = Paths.get(luceneBase)
        Napier.i {
            "use file system lucene ${p.toFile().canonicalPath}"
        }
        p to false
    }
    return b(path, isInMemory)
}
