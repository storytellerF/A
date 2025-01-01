package com.storyteller_f.index

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.MgetRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
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

private const val TOPIC_INDEX_NAME = "topics"

class ElasticTopicSearchService(private val connection: ElasticConnection) : TopicSearchService {

    override suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit> {
        return if (topics.size == 1) {
            val topic = topics.first()
            useElasticClient(connection) {
                val response = index {
                    it.index(TOPIC_INDEX_NAME).id(topic.id.toString()).document(topic)
                }.await()
                Napier.i(tag = "elastic save") {
                    response.toString()
                }
            }
        } else {
            useElasticClient(connection) {
                val response = bulk(BulkRequest.of {
                    it.operations(topics.map { document ->
                        BulkOperation.of { op ->
                            op.index(
                                IndexOperation.Builder<TopicDocument>()
                                    .index(TOPIC_INDEX_NAME)
                                    .id(document.id.toString()) // 指定文档 ID
                                    .document(document)
                                    .build()
                            )
                        }
                    })
                }).await()
                Napier.i(tag = "elastic save bulk") {
                    response.toString()
                }
            }
        }
    }

    override suspend fun getDocument(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        return if (idList.size == 1) {
            val id = idList.first()
            useElasticClient(connection) {
                listOf(get({
                    it.index(TOPIC_INDEX_NAME)
                        .id(id.toString())
                }, TopicDocument::class.java).await().source())
            }
        } else {
            useElasticClient(connection) {
                mget(MgetRequest.of { builder ->
                    builder.index(TOPIC_INDEX_NAME).ids(idList.map { it.toString() })
                }, TopicDocument::class.java).await().docs().map {
                    it.result().source()
                }
            }
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useElasticClient(connection) {
            if (indices().exists {
                    it.index(TOPIC_INDEX_NAME)
                }.await().value()) {
                val response = indices().delete {
                    it.index(TOPIC_INDEX_NAME)
                }.await()
                Napier.i {
                    "elastic clean $response"
                }
            } else {
                Unit
            }
        }
    }

    override suspend fun searchDocument(
        size: Int,
        word: List<String>?,
        nextTopicId: PrimaryKey?,
        author: PrimaryKey?,
        root: Pair<PrimaryKey?, ObjectType>?,
        parent: Pair<PrimaryKey?, ObjectType>?
    ): Result<PaginationResult<TopicDocument>> {
        val boolQuery = createTopicSearchQuery(word, root, parent, author, nextTopicId)

        // 构建排序条件：按 ID 升序排序
        val request = SearchRequest.of { s ->
            s.index(TOPIC_INDEX_NAME) // 指定索引名称
                .query(boolQuery)
                .sort { sort ->
                    sort.field { f ->
                        f.field("id").order(SortOrder.Asc)
                    }
                }.trackScores(true)
        }
        Napier.i {
            "elastic search query $request"
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
        root: Pair<PrimaryKey?, ObjectType>?,
        parent: Pair<PrimaryKey?, ObjectType>?,
        author: PrimaryKey?,
        nextTopicId: PrimaryKey?
    ): Query {
        val queryList = buildList {
            word?.let {
                it.filter { w -> w.isNotBlank() }.takeIf { list -> list.isNotEmpty() }?.let {
                    add(MatchQuery.of { m ->
                        m.field("content")
                            .query(it.joinToString(" ")) // 多关键字匹配，忽略大小写
                    }._toQuery())
                }
            }

            root?.let {
                add(createRootSearchQuery(it))
            }

            parent?.let {
                add(createParentSearchQuery(it))
            }

            author?.let {
                add(TermQuery.of { t ->
                    t.field("author").value(it)
                }._toQuery())
            }
            nextTopicId?.let {
                add(RangeQuery.of { r ->
                    r.number {
                        it.field("id")
                            .lt(nextTopicId.toDouble())
                    }
                }._toQuery())
            }
        }

        Napier.i {
            "query list size ${queryList.size} $queryList"
        }
        return if (queryList.size == 1) {
            queryList.first()
        } else {
            BoolQuery.of { b ->
                queryList.forEach {
                    b.must(it)
                }
                b
            }._toQuery()
        }
    }

    private fun createRootSearchQuery(pair: Pair<PrimaryKey?, ObjectType>): Query = BoolQuery.of { b ->
        pair.first?.let {
            b.must(TermQuery.of { t ->
                t.field("rootId").value(it)
            }._toQuery())
        }
        b.must(MatchQuery.of { t ->
            t.field("rootType").query(pair.second.name)
        }._toQuery())
    }._toQuery()

    private fun createParentSearchQuery(pair: Pair<PrimaryKey?, ObjectType>): Query {
        val queryList = buildList {
            pair.first?.let {
                add(TermQuery.of { t ->
                    t.field("parentId").value(it)
                }._toQuery())
            }
            add(TermQuery.of { t ->
                t.field("parentType.keyword").value(pair.second.name)
            }._toQuery())
        }
        return if (queryList.size == 1) {
            queryList.first()
        } else {
            BoolQuery.of { b ->
                queryList.forEach {
                    b.must(it)
                }
                b
            }._toQuery()
        }
    }
}

private suspend fun <T> useElasticClient(
    elasticConnection: ElasticConnection,
    block: suspend ElasticsearchAsyncClient.() -> T
): Result<T> {
    val point = Exception()
    val sslContext = if (elasticConnection.certFile.isNotBlank()) {
        val crtStream = withContext(Dispatchers.IO) {
            Napier.i(message = "cert path ${File(elasticConnection.certFile).canonicalPath}")
            FileInputStream(elasticConnection.certFile)
        }
        TransportUtils.sslContextFromHttpCaCrt(crtStream)
    } else {
        null
    }

    val credsProv = BasicCredentialsProvider().apply {
        setCredentials(
            AuthScope.ANY,
            UsernamePasswordCredentials(elasticConnection.name, elasticConnection.pass)
        )
    }
    return runCatching {
        try {
            RestClient
                .builder(HttpHost.create(elasticConnection.url))
                .setHttpClientConfigCallback { p0 ->
                    if (sslContext != null) {
                        p0.setSSLContext(sslContext)
                    }
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
                Napier.e(throwable = point) {
                    "elastic error $e"
                }
                throw e
            }
        }
    }
}
