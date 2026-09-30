/*
 * This is a private project. All rights reserved.
 */
package com.storyteller_f.a.cloud.lucene

import com.storyteller_f.a.backend.core.service.LuceneRpc
import com.storyteller_f.a.backend.core.service.RpcLuceneDocument
import com.storyteller_f.a.backend.core.service.RpcLuceneQuery
import com.storyteller_f.a.backend.core.service.RpcLuceneResult
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories

private const val ID_FIELD = "id1"
private const val SORT_ID_FIELD = "id2"
private const val PAYLOAD_FIELD = "_payload"

internal class LuceneRpcImpl(private val base: Path) : LuceneRpc {
    private val analyzer = StandardAnalyzer()
    private val indexes = ConcurrentHashMap<String, Mutex>()
    init {
        base.createDirectories()
    }
    override suspend fun health() = "ok"
    override suspend fun save(index: String, documents: List<RpcLuceneDocument>) =
        useIndex(index) {
        IndexWriter(this, IndexWriterConfig(analyzer)).use { it.addDocuments(documents.map(::toDocument)) }
        Unit
    }
    override suspend fun get(index: String, ids: List<Long>): List<String?> =
        useIndex(index) {
        if (ids.isEmpty()) return@useIndex emptyList()
        try {
            DirectoryReader.open(this).use { reader ->
                val searcher = IndexSearcher(reader)
                val found =
                    searcher.search(LongPoint.newSetQuery(ID_FIELD, ids), ids.size).scoreDocs.associate { hit ->
                        val document = searcher.storedFields().document(hit.doc)
                        document.get(ID_FIELD).toLong() to readPayload(document)
                    }
                ids.map(found::get)
            }
        } catch (_: IndexNotFoundException) {
            ids.map { null }
        }
    }
    override suspend fun clean(index: String) =
        useIndex(index) {
        IndexWriter(this, IndexWriterConfig(analyzer)).use { it.deleteDocuments(MatchAllDocsQuery.INSTANCE) }
        Unit
    }
    override suspend fun delete(index: String, query: RpcLuceneQuery) =
        useIndex(index) {
        IndexWriter(this, IndexWriterConfig(analyzer)).use { it.deleteDocuments(buildQuery(query)) }
        Unit
    }
    override suspend fun search(index: String, query: RpcLuceneQuery): RpcLuceneResult =
        useIndex(index) {
        require(query.offset >= 0 && query.size > 0) { "invalid pagination" }
        val limit = Math.addExact(query.offset, query.size)
        try {
            DirectoryReader.open(this).use { reader ->
                val searcher = IndexSearcher(reader)
                val sort =
                    if (query.sortByIdDescending) {
                        Sort(
                            SortField(SORT_ID_FIELD, SortField.Type.LONG, true),
                        )
                    } else {
                        Sort.RELEVANCE
                    }
                val hits = searcher.search(buildQuery(query), limit, sort)
                val payloads =
                    hits.scoreDocs.drop(
                        query.offset,
                    ).map { readPayload(searcher.storedFields().document(it.doc)) }
                RpcLuceneResult(payloads, hits.totalHits.value)
            }
        } catch (_: IndexNotFoundException) {
            RpcLuceneResult(emptyList(), 0)
        }
    }
    private suspend fun <T> useIndex(index: String, block: FSDirectory.() -> T): T =
        withContext(Dispatchers.IO) {
        require(index.matches(Regex("[a-zA-Z0-9_-]+"))) { "invalid index name" }
        val path = base.resolve(index)
        require(!Files.isSymbolicLink(path)) { "symbolic index paths are not allowed" }
        indexes.computeIfAbsent(index) { Mutex() }.withLock {
            FSDirectory.open(path.createDirectories()).use(block)
        }
    }
    private fun toDocument(source: RpcLuceneDocument) =
        Document().apply {
        val names = source.textFields.keys + source.keywordFields.keys + source.longFields.keys
        require(
            names.none { it in setOf(ID_FIELD, SORT_ID_FIELD, PAYLOAD_FIELD) || it.isBlank() },
        ) { "invalid or reserved field name" }
        add(LongField(ID_FIELD, source.id, Field.Store.YES))
        add(NumericDocValuesField(SORT_ID_FIELD, source.id))
        add(StoredField(PAYLOAD_FIELD, source.payload))
        source.textFields.forEach { (name, value) -> add(TextField(name, value, Field.Store.NO)) }
        source.keywordFields.forEach { (name, value) -> add(StringField(name, value, Field.Store.NO)) }
        source.longFields.forEach { (name, value) -> add(LongField(name, value, Field.Store.NO)) }
    }

    private fun readPayload(document: Document): String =
        document.get(PAYLOAD_FIELD) ?: buildJsonObject {
        // Earlier indexes stored fields individually. Preserve those records as generic JSON during migration.
        document.fields.forEach { field ->
            val name = if (field.name() == ID_FIELD) "id" else field.name()
            val value = field.numericValue()?.let(::JsonPrimitive) ?: JsonPrimitive(field.stringValue())
            put(name, value)
        }
    }.toString()
    private fun buildQuery(source: RpcLuceneQuery) =
        BooleanQuery.Builder().apply {
        source.mustLong.forEach { (field, value) ->
            add(
                LongPoint.newExactQuery(field, value),
                BooleanClause.Occur.MUST,
            )
        }
        source.mustNotLong.forEach { (field, value) ->
            add(
                LongPoint.newExactQuery(field, value),
                BooleanClause.Occur.MUST_NOT,
            )
        }
        source.mustLongSet.forEach { (field, values) ->
            add(
                LongPoint.newSetQuery(field, values),
                BooleanClause.Occur.MUST,
            )
        }
        source.mustKeyword.forEach { (field, value) -> add(TermQuery(Term(field, value)), BooleanClause.Occur.MUST) }
        source.text.forEach { text -> addTextQuery(text) }
        val filters =
            listOf(
                source.mustLong.size,
                source.mustLongSet.size,
                source.mustKeyword.size,
                source.text.size,
            )
        if (filters.all { it == 0 }) {
            add(MatchAllDocsQuery.INSTANCE, BooleanClause.Occur.MUST)
        }
    }.build()
}

fun main() {
    val port = System.getenv("LUCENE_RPC_PORT")?.toIntOrNull() ?: 8821
    val service = LuceneRpcImpl(Paths.get(System.getenv("LUCENE_BASE_PATH") ?: "/data"))
    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(WebSockets) { maxFrameSize = Long.MAX_VALUE }
        install(Krpc)
        routing {
            get(
                "/health",
            ) {
                call.respondText(
                    "ok",
                )
            }
            rpc("/rpc") {
                rpcConfig { serialization { json() } }
                registerService<LuceneRpc> { service }
            }
        }
    }.start(wait = true)
}
