package com.storyteller_f.index

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.TransportUtils
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.storyteller_f.ElasticConnection
import com.storyteller_f.shared.type.OKey
import kotlinx.coroutines.future.await
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import java.io.File

class ElasticTopicDocumentService(private val connection: ElasticConnection) : TopicDocumentService {
    override suspend fun saveDocument(topics: List<TopicDocument>) {
        useElasticClient(connection) {
            topics.map { topic ->
                index {
                    it.index("topics").id(topic.id.toString()).document(topic)
                }
            }.forEach {
                it.await()
            }
        }
    }

    override suspend fun getDocument(idList: List<OKey>): List<TopicDocument?> {
        return useElasticClient(connection) {
            idList.map { id ->
                get({
                    it.index("topics")
                        .id(id.toString())
                }, TopicDocument::class.java)
            }.map {
                it.await().source()
            }

        }
    }

    override suspend fun clean() {
        useElasticClient(connection) {
            indices().delete {
                it.index("topics")
            }
        }
    }

}

private suspend fun <T> useElasticClient(
    elasticConnection: ElasticConnection,
    block: suspend ElasticsearchAsyncClient.() -> T
): T {
    val sslContext = TransportUtils
        .sslContextFromHttpCaCrt(ElasticConnection::class.java.classLoader!!.getResourceAsStream("ca.crt"))

    val credsProv = BasicCredentialsProvider()
    credsProv.setCredentials(
        AuthScope.ANY, UsernamePasswordCredentials(elasticConnection.name, elasticConnection.pass)
    )
    return RestClient
        .builder(HttpHost.create(elasticConnection.url))
        .setHttpClientConfigCallback { p0 ->
            p0.setSSLContext(sslContext)
            p0.setDefaultCredentialsProvider(credsProv)
        }
        .build().use { restClient ->
            RestClientTransport(
                restClient, JacksonJsonpMapper().apply {
                    objectMapper().registerKotlinModule()
                }
            ).use { transport ->
                ElasticsearchAsyncClient(transport).block()
            }
        }
}
