package com.storyteller_f.index

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Operator
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.TransportUtils
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.storyteller_f.ElasticConnection
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.types.PaginationResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import java.io.File
import java.io.FileInputStream

class ElasticTopicDocumentService(private val connection: ElasticConnection) : TopicDocumentService {
    override suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit> {
        return useElasticClient(connection) {
            topics.map { topic ->
                index {
                    it.index("topics").id(topic.id.toString()).document(topic)
                }
            }.forEach {
                it.await()
            }
        }
    }

    override suspend fun getDocument(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        return useElasticClient(connection) {
            idList.map { id ->
                get({
                    it.index("topics")
                        .id(id.toString())
                }, TopicDocument::class.java)
            }.map {
                it.await()
            }.map {
                it.source()
            }
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useElasticClient(connection) {
            indices().delete {
                it.index("topics")
            }.await()
            Unit
        }
    }

    override suspend fun searchDocument(
        word: List<String>,
        size: Int,
        nextTopicId: PrimaryKey?
    ): Result<PaginationResult<TopicDocument>> {
        val contentQuery = MatchQuery.of { m ->
            m.field("content")
                .query(word.joinToString(" ")) // 多关键字匹配，忽略大小写
                .operator(Operator.Or)
        }._toQuery()

        val idRangeQuery = RangeQuery.of { r ->
            r.field("id")
                .lt(JsonData.of(nextTopicId ?: Long.MAX_VALUE))
        }._toQuery()

        val boolQuery = BoolQuery.of { b ->
            b.must(contentQuery).must(idRangeQuery)
        }

        // 构建排序条件：按 ID 升序排序
        val request = SearchRequest.of { s ->
            s.index("topics") // 指定索引名称
                .query(boolQuery._toQuery())
                .sort { sort ->
                    sort.field { f ->
                        f.field("id").order(SortOrder.Asc)
                    }
                }.trackScores(true)
        }
        return useElasticClient(connection) {
            val response = search<TopicDocument>(request, TopicDocument::class.java).await()
            val hits = response.hits()
            val total = hits.total()
            PaginationResult(hits.hits().mapNotNull {
                it.source()
            }, total?.value() ?: 0)
        }
    }
}

private suspend fun <T> useElasticClient(
    elasticConnection: ElasticConnection,
    block: suspend ElasticsearchAsyncClient.() -> T
): Result<T> {
    val crtStream = withContext(Dispatchers.IO) {
        Napier.i(message = "cert path ${File(elasticConnection.certFile).canonicalPath}")
        FileInputStream(elasticConnection.certFile)
    }
    val sslContext = TransportUtils
        .sslContextFromHttpCaCrt(crtStream)

    val credsProv = BasicCredentialsProvider()
    credsProv.setCredentials(
        AuthScope.ANY,
        UsernamePasswordCredentials(elasticConnection.name, elasticConnection.pass)
    )
    return runCatching {
        RestClient
            .builder(HttpHost.create(elasticConnection.url))
            .setHttpClientConfigCallback { p0 ->
                p0.setSSLContext(sslContext)
                p0.setDefaultCredentialsProvider(credsProv)
            }
            .build().use { restClient ->
                RestClientTransport(
                    restClient,
                    JacksonJsonpMapper().apply {
                        objectMapper().registerKotlinModule()
                    }
                ).use { transport ->
                    ElasticsearchAsyncClient(transport).block()
                }
            }
    }
}
