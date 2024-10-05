package com.storyteller_f

import com.storyteller_f.index.ElasticTopicDocumentService
import com.storyteller_f.index.LuceneTopicDocumentService
import com.storyteller_f.index.TopicDocumentService
import com.storyteller_f.media.FileSystemMediaService
import com.storyteller_f.media.MediaService
import com.storyteller_f.media.MinIoMediaService
import com.storyteller_f.naming.NameService
import java.nio.file.Paths
import java.util.*

class Backend(
    val config: Config,
    val topicDocumentService: TopicDocumentService,
    val mediaService: MediaService,
    val nameService: NameService
)

class Config(
    val databaseConnection: DatabaseConnection,
    val hmacKey: String
)

data class ElasticConnection(val url: String, val certFile: String, val name: String, val pass: String)
data class MinIoConnection(val url: String, val user: String, val pass: String)
data class DatabaseConnection(val uri: String, val driver: String, val user: String, val password: String)

fun readEnv(): MutableMap<out Any, out Any> {
    val map = ClassLoader.getSystemClassLoader().getResourceAsStream(".env")?.use {
        Properties().apply {
            load(it)
        }
    } ?: System.getenv()
    return map
}

fun buildBackendFromEnv(env: Map<out Any, Any>): Backend {

    println("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val hmac = env["HMAC_KEY"] as String

    val config = Config(databaseConnection, hmac)

    val topicDocumentService = topicDocumentService(env)
    val mediaService = mediaService(env)

    return Backend(
        config,
        topicDocumentService,
        mediaService,
        NameService()
    )
}

private fun mediaService(map: Map<out Any, Any>): MediaService {
    return when (map["MEDIA_SERVICE"]) {
        "minio" -> {
            val url = map["MINIO_URL"] as String
            val name = map["MINIO_NAME"] as String
            val pass = map["MINIO_PASS"] as String
            MinIoMediaService(MinIoConnection(url, name, pass))
        }
        "filesystem" -> FileSystemMediaService()
        else -> throw UnsupportedOperationException("unsupported media service type ${map["MEDIA_SERVICE"]}")
    }
}

private fun topicDocumentService(
    map: Map<out Any, Any>,
): TopicDocumentService {
    val path = Paths.get("../deploy/lucene_data/index")
    return when(val type = map["SEARCH_SERVICE"]) {
        "elastic" -> {
            val certFile = map["CERT_FILE"] as String
            val url = map["ELASTIC_URL"] as String
            val name = map["ELASTIC_NAME"] as String
            val pass = map["ELASTIC_PASSWORD"] as String
            ElasticTopicDocumentService(ElasticConnection(url, certFile, name, pass))
        }
        "lucene" -> LuceneTopicDocumentService(path)
        else -> throw UnsupportedOperationException("unsupported search service type $type")
    }
}

private fun databaseConnection(properties: Map<out Any, Any>): DatabaseConnection {
    val uri = properties["DATABASE_URI"] as String
    val driver = properties["DATABASE_DRIVER"] as String
    val user = properties["DATABASE_USER"] as String
    val pass = properties["DATABASE_PASS"] as String
    return DatabaseConnection(uri, driver, user, pass)
}
