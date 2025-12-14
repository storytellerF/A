package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.TransportUtils
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
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

abstract class Elastic(val connection: ElasticConnection) {
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
            if (e is ElasticsearchException) {
                Napier.e {
                    "elastic search failed ${e.status()}"
                }
            }
            Result.failure(
                when (e) {
                    is ConnectException,
                    is ConnectionClosedException ->
                        Exception("elastic service unavailable", e)

                    is ElasticsearchException if e.status() == 404 ->
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
    if (indices().exists {
            it.index(indexName)
        }.await().value()) {
        val response = indices().delete {
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
    connection: ElasticConnection,
    documents: List<T>,
    indexName: String
) {
    if (documents.size == 1) {
        val topic = documents.first()
        val response = index {
            it.index(indexName).id(topic.id.toString()).document(topic)
                .refresh(if (connection.refresh) Refresh.WaitFor else Refresh.False)
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
    return try {
        val response = search(request, clazz).await()
        val hits = response.hits()
        val total = hits.total()?.value()
        Napier.i {
            "size: ${hits.hits().size} maxScore: ${hits.maxScore()} total: $total"
        }
        PaginationResult(hits.hits().mapNotNull {
            it.source()
        }, total ?: 0)
    } catch (e: Exception) {
        Napier.e(e) {
            "elastic search failed"
        }
        PaginationResult(emptyList(), 0)
    }
}

fun <T> buildElasticSearchService(env: MergedEnv, b: (ElasticConnection) -> T): T {
    val certFile = env["ELASTIC_CERT_FILE"] ?: throw Exception("ELASTIC_CERT_FILE is empty")
    val url = env["ELASTIC_URL"] ?: throw Exception("ELASTIC_URL is empty")
    val name = env["ELASTIC_NAME"] ?: throw Exception("ELASTIC_NAME is empty")
    val pass = env["ELASTIC_PASSWORD"] ?: throw Exception("ELASTIC_PASSWORD is empty")
    val connection = ElasticConnection(url, certFile, name, pass, env["BUILD_TYPE"] == "test")
    return b(connection)
}
