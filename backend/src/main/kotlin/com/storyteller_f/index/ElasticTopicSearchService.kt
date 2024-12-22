package com.storyteller_f.index

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Operator
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.MgetRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.TransportUtils
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.storyteller_f.ElasticConnection
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.types.PaginationResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.apache.http.ConnectionClosedException
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import java.io.File
import java.io.FileInputStream
import java.net.ConnectException

private const val topicIndexName = "topics"

class ElasticTopicSearchService(private val connection: ElasticConnection) : TopicSearchService {

    override suspend fun saveDocument(topics: List<TopicDocument>): Result<Long> {
        return if (topics.size == 1) {
            val topic = topics.first()
            useElasticClient(connection) {
                index {
                    it.index(topicIndexName).id(topic.id.toString()).document(topic)
                }.await().seqNo()!!
            }
        } else {
            useElasticClient(connection) {
                bulk(BulkRequest.of {
                    it.operations(topics.map { document ->
                        BulkOperation.of { op ->
                            op.index(
                                IndexOperation.Builder<TopicDocument>()
                                    .index(topicIndexName)
                                    .id(document.id.toString()) // 指定文档 ID
                                    .document(document)
                                    .build()
                            )
                        }
                    })
                }).await().items().size.toLong()
            }
        }
    }

    override suspend fun getDocument(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        return if (idList.size == 1) {
            useElasticClient(connection) {
                val id = idList.first()
                listOf(get({
                    it.index(topicIndexName)
                        .id(id.toString())
                }, TopicDocument::class.java).await().source())
            }
        } else {
            useElasticClient(connection) {
                mget(MgetRequest.of {
                    it.index(topicIndexName).ids(idList.map { it.toString() })
                }, TopicDocument::class.java).await().docs().map {
                    it.result().source()
                }
            }
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useElasticClient(connection) {
            indices().delete {
                it.index(topicIndexName)
            }.await()
            Unit
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
        val boolQuery = createTopicSearchQuery(word, root, parent, author, nextTopicId)

        println(boolQuery.toString())

        // 构建排序条件：按 ID 升序排序
        val request = SearchRequest.of { s ->
            s.index(topicIndexName) // 指定索引名称
                .query(boolQuery._toQuery())
                .sort { sort ->
                    sort.field { f ->
                        f.field("id").order(SortOrder.Asc)
                    }
                }.trackScores(true)
        }
        return useElasticClient(connection) {
            val response = search(request, TopicDocument::class.java).await()
            val hits = response.hits()
            val total = hits.total()
            PaginationResult(hits.hits().mapNotNull {
                it.source()
            }, total?.value() ?: 0)
        }
    }

    private fun createTopicSearchQuery(
        word: List<String>?,
        root: Pair<PrimaryKey, ObjectType>?,
        parent: Pair<PrimaryKey, ObjectType>?,
        author: PrimaryKey?,
        nextTopicId: PrimaryKey?
    ): BoolQuery {
        val contentQuery = word?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let {
            MatchQuery.of { m ->
                m.field("content")
                    .query(it.joinToString(" ")) // 多关键字匹配，忽略大小写
                    .operator(Operator.Or)
            }._toQuery()
        }

        val rootQuery = root?.let {
            createRootSearchQuery(it)
        }

        val parentQuery = parent?.let {
            createParentSearchQuery(it)
        }

        val authorQuery = author?.let {
            TermQuery.of { t ->
                t.field("author").value(it)
            }._toQuery()
        }

        val idRangeQuery = RangeQuery.of { r ->
            r.field("id")
                .lt(JsonData.of(nextTopicId ?: Long.MAX_VALUE))
        }._toQuery()

        return BoolQuery.of { b ->
            contentQuery?.let {
                b.must(it)
            }
            b.must(idRangeQuery)
            rootQuery?.let {
                b.must(it)
            }
            parentQuery?.let {
                b.must(it)
            }
            authorQuery?.let {
                b.must(it)
            }
            b
        }
    }

    private fun createRootSearchQuery(pair: Pair<PrimaryKey, ObjectType>): Query = BoolQuery.of { b ->
        b.must(TermQuery.of { t ->
            t.field("rootId").value(pair.first)
        }._toQuery()).must(TermQuery.of { t ->
            t.field("rootType").value(pair.second.name)
        }._toQuery())
    }._toQuery()

    private fun createParentSearchQuery(pair: Pair<PrimaryKey, ObjectType>): Query = BoolQuery.of { b ->
        b.must(TermQuery.of { t ->
            t.field("parentId").value(pair.first)
        }._toQuery()).must(TermQuery.of { t ->
            t.field("parentType").value(pair.second.name)
        }._toQuery())
    }._toQuery()
}

private suspend fun <T> useElasticClient(
    elasticConnection: ElasticConnection,
    block: suspend ElasticsearchAsyncClient.() -> T
): Result<T> {
    val sslContext = if (elasticConnection.certFile.isNotBlank()) {
        val crtStream = withContext(Dispatchers.IO) {
            Napier.i(message = "cert path ${File(elasticConnection.certFile).canonicalPath}")
            FileInputStream(elasticConnection.certFile)
        }
        TransportUtils.sslContextFromHttpCaCrt(crtStream)
    } else {
        null
    }

    val credsProv = BasicCredentialsProvider()
    credsProv.setCredentials(
        AuthScope.ANY,
        UsernamePasswordCredentials(elasticConnection.name, elasticConnection.pass)
    )
    return runCatching {
        try {
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
        } catch (e: Exception) {
            if (e is ConnectException || e is ConnectionClosedException) {
                throw Exception("elastic service unavailable", e)
            } else {
                throw e
            }
        }
    }
}
