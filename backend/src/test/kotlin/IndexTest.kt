import com.storyteller_f.ElasticConnection
import com.storyteller_f.index.ElasticTopicSearchService
import com.storyteller_f.index.LuceneTopicSearchService
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.index.TopicSearchService
import kotlinx.coroutines.runBlocking
import org.testcontainers.elasticsearch.ElasticsearchContainer
import kotlin.io.path.Path
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
                val service = ElasticTopicSearchService(
                    ElasticConnection(
                        elasticClient.httpHostAddress,
                        "",
                        "elastic",
                        "changeme"
                    )
                )
                block(service)
            }
        block(LuceneTopicSearchService(Path("../deploy_test/lucene_data/index")))
    }
}