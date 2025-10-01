import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.elastic.ElasticTopicSearchService
import com.storyteller_f.a.backend.lucene.LuceneTopicSearchService
import com.storyteller_f.a.backend.lucene.buildLuceneSearchService
import kotlinx.coroutines.runBlocking
import org.testcontainers.elasticsearch.ElasticsearchContainer
import kotlin.test.Test

class IndexTest {
    @Test
    fun `test save multi document`() {
        testIndex {
            it.saveDocument(
                listOf(
                    TopicDocument(0, "test", 0, "ROOM", 0, "ROOM", 0),
                    TopicDocument(1, "test", 1, "ROOM", 1, "ROOM", 1)
                )
            ).getOrThrow()
        }
    }
}

fun testIndex(block: suspend (TopicSearchService) -> Unit) {
    runBlocking {
        ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.17.0"
        )
            // disable SSL
            .withEnv("xpack.security.transport.ssl.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false").use { elasticClient ->
                elasticClient.start()
                val connection = ElasticConnection(
                    "http://${elasticClient.httpHostAddress}",
                    "",
                    "elastic",
                    "changeme"
                )
                val service = ElasticTopicSearchService(connection)
                block(service)
            }
        block(
            buildLuceneSearchService(MergedEnv(listOf())) { path, isInMemory ->
                LuceneTopicSearchService(path, isInMemory)
            }
        )
    }
}
