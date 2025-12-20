package com.storyteller_f.a.backend.lucene

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.OffsetFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.WildcardQuery
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
    fetch: OffsetFetch?,
    sortById: Sort,
    t: T
): PaginationResult<D> {
    return try {
        DirectoryReader.open(this).use { reader ->
            val searcher = IndexSearcher(reader)
            val offset = fetch?.cursor?.value ?: 0
            val limit = offset + (fetch?.size ?: 10)
            val docs = searcher.search(combinedQuery, limit, sortById)
            val scoreDocs = docs.scoreDocs
            val list = if (scoreDocs.size > offset) {
                scoreDocs.slice(offset until scoreDocs.size).mapNotNull { doc ->
                    searcher.storedFields().document(doc.doc)?.let { document ->
                        val id = document.get("id1").toPrimaryKeyOrNull()
                        if (id != null) {
                            t.restore(id, document)
                        } else {
                            null
                        }
                    }
                }
            } else {
                emptyList()
            }
            PaginationResult(list, docs.totalHits.value)
        }
    } catch (_: IndexNotFoundException) {
        PaginationResult(emptyList(), 0)
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

fun BooleanQuery.Builder.addMatchQuery(analyzer: StandardAnalyzer, word: String, field: String) {
    val parser = MultiFieldQueryParser(arrayOf(field), analyzer)
    val keyword = preprocessUserInputKeyword(word)
    val parse = parser.parse(keyword)
    add(parse, BooleanClause.Occur.MUST)
}

fun BooleanQuery.Builder.addPrefixAndInclusionQuery(word: String, field: String) {
    val keyword = preprocessUserInputKeyword(word)
    if (keyword.isBlank()) return

    val prefixQuery = BoostQuery(PrefixQuery(Term(field, keyword)), 10.0f)
    val inclusionQuery = BoostQuery(WildcardQuery(Term(field, "*$keyword*")), 1.0f)

    add(
        BooleanQuery.Builder().apply {
            add(prefixQuery, BooleanClause.Occur.SHOULD)
            add(inclusionQuery, BooleanClause.Occur.SHOULD)
        }.build(),
        BooleanClause.Occur.MUST
    )
}

fun BooleanQuery.Builder.addPrioritizedFieldsQuery(word: String, aidField: String, nameField: String) {
    val keyword = preprocessUserInputKeyword(word)
    if (keyword.isBlank()) return

    add(
        BooleanQuery.Builder().apply {
            // 1. aid 前缀匹配 (最高优先级)
            add(BoostQuery(PrefixQuery(Term(aidField, keyword)), 1000f), BooleanClause.Occur.SHOULD)
            // 2. name 前缀匹配
            add(BoostQuery(PrefixQuery(Term(nameField, keyword)), 100f), BooleanClause.Occur.SHOULD)
            // 3. aid 包含匹配
            add(BoostQuery(WildcardQuery(Term(aidField, "*$keyword*")), 10f), BooleanClause.Occur.SHOULD)
            // 4. name 包含匹配
            add(BoostQuery(WildcardQuery(Term(nameField, "*$keyword*")), 1f), BooleanClause.Occur.SHOULD)
        }.build(),
        BooleanClause.Occur.MUST
    )
}
