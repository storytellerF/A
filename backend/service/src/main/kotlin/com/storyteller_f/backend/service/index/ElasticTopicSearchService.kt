package com.storyteller_f.backend.service.index

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.*
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.TransportUtils
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.shared.type.ObjectType
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

private const val TOPIC_INDEX_NAME = "topics"

class ElasticTopicSearchService(private val connection: ElasticConnection) : TopicSearchService {

    override suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit> {
        return when {
            topics.isEmpty() -> Result.success(Unit)
            topics.size == 1 -> {
                val topic = topics.first()
                useElasticClient(connection) {
                    val response = index {
                        it.index(TOPIC_INDEX_NAME).id(topic.id.toString()).document(topic).refresh(Refresh.WaitFor)
                    }.await()
                    Napier.i(tag = "elastic save") {
                        response.toString()
                    }
                }
            }

            else -> {
                useElasticClient(connection) {
                    val response = bulk {
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
                        }).refresh(Refresh.WaitFor)
                    }.await()
                    Napier.i(tag = "elastic save bulk") {
                        "errors:${response.errors()} took: ${response.took()}"
                    }
                }
            }
        }
    }

    override suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>> {
        return when {
            idList.isEmpty() -> Result.success(emptyList())
            idList.size == 1 -> {
                useElasticClient(connection) {
                    idList.map { id ->
                        get({
                            it.index(TOPIC_INDEX_NAME).id(id.toString())
                        }, TopicDocument::class.java).await().source()
                    }
                }
            }

            else -> {
                useElasticClient(connection) {
                    mget({
                        it.index(TOPIC_INDEX_NAME).ids(idList.map { it.toString() })
                    }, TopicDocument::class.java).await().docs().map {
                        it.result().source()
                    }
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
                    "elastic clean done $response"
                }
            } else {
                Napier.i {
                    "elastic index not exists"
                }
            }
        }
    }

    override suspend fun searchDocument(
        words: List<String>?,
        documentSearch: DocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?,
    ): Result<PaginationResult<TopicDocument>> {
        val boolQuery =
            createTopicSearchQuery(
                words,
                documentSearch,
                primaryKeyFetch
            )

        // 构建排序条件：按 ID 升序排序
        val request = SearchRequest.of { s ->
            s.index(TOPIC_INDEX_NAME) // 指定索引名称
                .query(boolQuery)
                .size(primaryKeyFetch?.size ?: 10)
                .sort { sort ->
                    sort.field { f ->
                        val sortOrder = when {
                            primaryKeyFetch == null -> null
                            primaryKeyFetch.cursor is Cursor.PreCursor<PrimaryKey> -> SortOrder.Asc
                            else -> null
                        } ?: SortOrder.Desc
                        f.field("id").order(sortOrder)
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
        words: List<String>?,
        documentSearch: DocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?,
    ): Query {
        val queryList = buildList {
            words?.let {
                it.filter { w -> w.isNotBlank() }.takeIf { list -> list.isNotEmpty() }?.let { filteredWordList ->
                    add(MatchQuery.of { m ->
                        m.field("content")
                            .query(filteredWordList.joinToString(" ")) // 多关键字匹配，忽略大小写
                    }._toQuery() to true)
                }
            }

            when {
                primaryKeyFetch == null -> {}
                primaryKeyFetch.cursor is Cursor.PreCursor<PrimaryKey> -> {
                    add(RangeQuery.of { r ->
                        r.untyped {
                            val cursor = primaryKeyFetch.cursor as Cursor.PreCursor<PrimaryKey>
                            it.field("id").gt(JsonData.of(cursor.value))
                        }
                    }._toQuery() to true)
                }

                primaryKeyFetch.cursor is Cursor.NextCursor<PrimaryKey> -> {
                    add(RangeQuery.of { r ->
                        r.untyped {
                            val cursor = primaryKeyFetch.cursor as Cursor.NextCursor<PrimaryKey>
                            it.field("id").lt(JsonData.of(cursor.value))
                        }
                    }._toQuery() to true)
                }
            }

            createTopicSearchQuery(documentSearch)
        }

        Napier.i {
            "query list size ${queryList.size} $queryList"
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

    private fun MutableList<Pair<Query, Boolean>>.createTopicSearchQuery(
        documentSearch: DocumentSearch,
    ) {
        when (documentSearch) {
            is DocumentSearch.Recommend -> {
                add(buildParentIdListTermSearch(documentSearch.communities) to true)
                add(TermQuery.of { t ->
                    t.field("author").value(documentSearch.uid)
                }._toQuery() to false)
            }

            DocumentSearch.RecommendNotLogin, DocumentSearch.CommunityRoot -> {
                add(TermQuery.of { t ->
                    t.field("parentType.keyword").value(ObjectType.COMMUNITY.name)
                }._toQuery() to true)
            }

            is DocumentSearch.Topics -> {
                add(buildParentIdListTermSearch(listOf(documentSearch.parentId)) to true)
            }

            DocumentSearch.All -> {}
        }
    }

    private fun buildParentIdListTermSearch(list: List<PrimaryKey>) = TermsQuery.of {
        it.field("parentId").terms { builder ->
            builder.value(list.map { id ->
                FieldValue.of(id)
            })
        }
    }._toQuery()
}

private suspend fun <T> useElasticClient(
    elasticConnection: ElasticConnection,
    block: suspend ElasticsearchAsyncClient.() -> T,
): Result<T> {
    val point = Exception()
    val certFile = elasticConnection.certFile
    Napier.i(message = "cert path ${File(certFile).canonicalPath}")
    val sslContext = if (certFile.isNotBlank()) {
        val crtStream = withContext(Dispatchers.IO) {
            FileInputStream(certFile)
        }
        TransportUtils.sslContextFromHttpCaCrt(crtStream)
    } else {
        null
    }

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
            it.block()
        }
    }.recoverResult { e ->
        Result.failure(
            when {
                e is ConnectException || e is ConnectionClosedException -> Exception("elastic service unavailable", e)
                e is ElasticsearchException && e.status() == 400 ->
                    Exception("index not found")
                else -> {
                    point.initCause(e)
                    point
                }
            })
    }
}
