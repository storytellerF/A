package com.storyteller_f

import com.storyteller_f.index.ElasticTopicDocumentService
import com.storyteller_f.index.LuceneTopicDocumentService
import com.storyteller_f.index.TopicDocumentService
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

fun buildBackendFromEnv(map: Map<out Any, Any>): Backend {

    println("load env: ${map["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(map)

    val elasticConnection1 = elasticConnection(map)

    val minIoConnection = minIoConnection(map)

    val hmac = map["HMAC_KEY"] as String

    val config = Config(databaseConnection, hmac)
    val path = Paths.get("./index/")
    return Backend(
        config,
        elasticConnection1?.let { ElasticTopicDocumentService(it) } ?: LuceneTopicDocumentService(path),
        MinIoMediaService(minIoConnection),
        NameService()
    )
}

private fun minIoConnection(properties: Map<out Any, Any>): MinIoConnection {
    val url = properties["MINIO_URL"] as String
    val name = properties["MINIO_NAME"] as String
    val pass = properties["MINIO_PASS"] as String
    return MinIoConnection(url, name, pass)
}

private fun elasticConnection(properties: Map<out Any, Any>): ElasticConnection? {
    val certFile = properties["CERT_FILE"] as? String ?: return null
    val url = properties["ELASTIC_URL"] as String
    val name = properties["ELASTIC_NAME"] as String
    val pass = properties["ELASTIC_PASSWORD"] as String
    return ElasticConnection(url, certFile, name, pass)
}

private fun databaseConnection(properties: Map<out Any, Any>): DatabaseConnection {
    val uri = properties["DATABASE_URI"] as String
    val driver = properties["DATABASE_DRIVER"] as String
    val user = properties["DATABASE_USER"] as String
    val pass = properties["DATABASE_PASS"] as String
    return DatabaseConnection(uri, driver, user, pass)
}
