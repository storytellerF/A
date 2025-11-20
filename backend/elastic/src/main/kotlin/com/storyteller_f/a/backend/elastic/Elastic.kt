package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.TransportUtils
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.recoverResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.apache.hc.core5.http.ConnectionClosedException
import org.apache.hc.core5.http.ContentType
import java.io.File
import java.io.FileInputStream
import java.net.ConnectException
import javax.net.ssl.SSLContext

abstract class Elastic(private val connection: ElasticConnection) {
    val sslContext = getSslContext(connection)

    suspend fun <T> useElasticClient(
        block: suspend ElasticsearchAsyncClient.() -> T,
    ): Result<T> {
        val elasticConnection = connection
        val point = Exception()
        return runCatching {
            ElasticsearchAsyncClient.of { b ->
                b.host(elasticConnection.url)
                    .usernameAndPassword(elasticConnection.name, elasticConnection.pass)
                    .sslContext(sslContext)
                    .transportOptions {
                        it.addHeader("Accept", ContentType.APPLICATION_JSON.mimeType)
                            .addHeader("Content-Type", ContentType.APPLICATION_JSON.mimeType)
                    }.jsonMapper(JacksonJsonpMapper().apply {
                        objectMapper().registerKotlinModule()
                    })
            }.use {
                withContext(Dispatchers.IO) {
                    it.block()
                }
            }
        }.recoverResult { e ->
            Result.failure(
                when (e) {
                    is ConnectException, is ConnectionClosedException -> Exception(
                        "elastic service unavailable",
                        e
                    )

                    is ElasticsearchException if e.status() == 400 ->
                        Exception("index not found", e)

                    else -> {
                        point.initCause(e)
                        point
                    }
                }
            )
        }
    }
}

suspend fun ElasticsearchAsyncClient.cleanAll(indexName: String) {
    if (exists {
            it.index(indexName)
        }.await().value()) {
        val response = delete {
            it.index(indexName)
        }.await()
        Napier.i {
            "elastic clean done $response"
        }
    } else {
        Napier.i {
            "elastic index not exists"
        }
    }
}

suspend fun <T> ElasticsearchAsyncClient.getDocumentList(
    idList: List<PrimaryKey>,
    indexName: String,
    clazz: Class<T>
): List<T?> {
    return if (idList.size == 1) {
        idList.map { id ->
            get({
                it.index(indexName).id(id.toString())
            }, clazz).await().source()
        }
    } else {
        mget({ builder ->
            builder.index(indexName).ids(idList.map { it.toString() })
        }, clazz).await().docs().map {
            it.result().source()
        }
    }
}

suspend fun <T : PrimaryKeyIdentifiable> ElasticsearchAsyncClient.saveDocumentList(
    documents: List<T>,
    indexName: String
) {
    if (documents.size == 1) {
        val topic = documents.first()
        val response = index {
            it.index(indexName).id(topic.id.toString()).document(topic)
                .refresh(Refresh.WaitFor)
        }.await()
        Napier.i(tag = "elastic save") {
            response.toString()
        }
    } else {
        val response = bulk {
            it.operations(documents.map { document ->
                BulkOperation.of { op ->
                    op.index(
                        IndexOperation.Builder<T>()
                            .index(indexName)
                            .id(document.id.toString()) // 指定文档 ID
                            .document(document)
                            .build()
                    )
                }
            }).refresh(Refresh.WaitFor)
        }.await()
        Napier.i(tag = "elastic save bulk") {
            "errors:${response.errors()} took: ${response.took()}ms"
        }
    }
}

fun getSslContext(elasticConnection: ElasticConnection): SSLContext? {
    val certFile = elasticConnection.certFile
    Napier.i(message = "cert path ${File(certFile).canonicalPath}")
    return if (certFile.isNotBlank()) {
        FileInputStream(certFile).use {
            TransportUtils.sslContextFromHttpCaCrt(it)
        }
    } else {
        null
    }
}

suspend fun <T> ElasticsearchAsyncClient.searchDocumentList(
    request: SearchRequest?,
    clazz: Class<T>
): PaginationResult<T> {
    val response = search(request, clazz).await()
    val hits = response.hits()
    val total = hits.total()
    return PaginationResult(hits.hits().mapNotNull {
        it.source()
    }, total?.value() ?: 0)
}

fun buildPrimaryKeyElasticSearchQuery(
    primaryKeyFetch: PrimaryKeyFetch?,
    createTopicSearchQuery: MutableList<Pair<Query, Boolean>>.() -> Unit
): Query {
    val queryList = buildList {
        when {
            primaryKeyFetch == null -> {}
            primaryKeyFetch.cursor is Cursor.AscCursor<PrimaryKey> -> {
                add(RangeQuery.of { r ->
                    r.untyped {
                        val cursor = primaryKeyFetch.cursor as Cursor.AscCursor<PrimaryKey>
                        it.field("id").gt(JsonData.of(cursor.value))
                    }
                }._toQuery() to true)
            }

            primaryKeyFetch.cursor is Cursor.DescCursor<PrimaryKey> -> {
                add(RangeQuery.of { r ->
                    r.untyped {
                        val cursor = primaryKeyFetch.cursor as Cursor.DescCursor<PrimaryKey>
                        it.field("id").lt(JsonData.of(cursor.value))
                    }
                }._toQuery() to true)
            }
        }

        createTopicSearchQuery()
    }

    return if (queryList.size == 1) {
        queryList.first().first
    } else {
        BoolQuery.of { b ->
            queryList.forEach {
                if (it.second) {
                    b.must(it.first)
                } else {
                    b.mustNot(it.first)
                }
            }
            b
        }._toQuery()
    }
}

fun MutableList<Pair<Query, Boolean>>.addMatchQuery(
    words: List<String>?,
    fieldName: String
) {
    preprocessUserInputKeyword(words)?.let {
        add(MatchQuery.of { m ->
            m.field(fieldName)
                .query(it) // 多关键字匹配，忽略大小写
        }._toQuery() to true)
    }
}

fun MutableList<Pair<Query, Boolean>>.addTermQuery(
    fieldName: String,
    termValue: PrimaryKey
) {
    add(TermQuery.of { t ->
        t.field(fieldName).value(termValue)
    }._toQuery() to false)
}

fun<T> buildElasticSearchService(env: MergedEnv, b: (ElasticConnection) -> T): T {
    val certFile = env["ELASTIC_CERT_FILE"] ?: throw Exception("ELASTIC_CERT_FILE is empty")
    val url = env["ELASTIC_URL"] ?: throw Exception("ELASTIC_URL is empty")
    val name = env["ELASTIC_NAME"] ?: throw Exception("ELASTIC_NAME is empty")
    val pass = env["ELASTIC_PASSWORD"] ?: throw Exception("ELASTIC_PASSWORD is empty")
    val connection = ElasticConnection(url, certFile, name, pass)
    return b(connection)
}
