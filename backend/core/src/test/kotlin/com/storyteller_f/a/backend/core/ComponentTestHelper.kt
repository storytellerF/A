package com.storyteller_f.a.backend.core

import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun buildMemorySearchEnv(): MergedEnv {
    return MergedEnv(
        listOf(
            mapOf(
                "SEARCH_SERVICE" to "lucene",
                "LUCENE_BASE_PATH" to "",
                "BUILD_TYPE" to "test"
            )
        )
    )
}

fun buildMemoryOssEnv(): MergedEnv {
    return MergedEnv(
        listOf(
            mapOf(
                "MEDIA_SERVICE" to "filesystem",
                "FILE_SYSTEM_MEDIA_PATH" to "",
                "SERVER_URL" to "http://localhost"
            )
        )
    )
}

@OptIn(ExperimentalUuidApi::class)
fun buildMemoryDatabaseConnection(): DatabaseConnection {
    val uuid = Uuid.random().toHexString()
    val h2File = File("./build/test/session/$uuid/h2/default")
    h2File.parentFile!!.let {
        if (!it.exists() && !it.mkdirs()) {
            throw Exception("mkdirs failed ${it.canonicalPath}")
        }
    }
    val url = "r2dbc:h2:file:///${h2File.path.replace("\\", "/")}"
    return DatabaseConnection(url, "h2", "sa", "")
}

fun useElasticSearchContainer(block: (MergedEnv) -> Unit) {
    System.setProperty("api.version", "1.44")
    ElasticsearchContainer(ContainerImages.ELASTICSEARCH)
        .withEnv("xpack.security.transport.ssl.enabled", "false")
        .withEnv("xpack.security.http.ssl.enabled", "false")
        .use { container ->
            container.start()
            val env = MergedEnv(
                listOf(
                    mapOf(
                        "SEARCH_SERVICE" to "elastic",
                        "ELASTIC_NAME" to "elastic",
                        "ELASTIC_PASSWORD" to "changeme",
                        "ELASTIC_URL" to "http://${container.httpHostAddress}",
                        "ELASTIC_CERT_FILE" to "",
                        "BUILD_TYPE" to "test"
                    )
                )
            )
            block(env)
        }
}

fun useMinioContainer(block: (MergedEnv) -> Unit) {
    MinIOContainer(ContainerImages.MINIO)
        .use { container ->
            container.start()
            val env = MergedEnv(
                listOf(
                    mapOf(
                        "MEDIA_SERVICE" to "minio",
                        "MINIO_URL" to container.s3URL,
                        "MINIO_NAME" to container.userName,
                        "MINIO_PASS" to container.password,
                    )
                )
            )
            block(env)
        }
}

fun usePostgresContainer(block: (DatabaseConnection) -> Unit) {
    System.setProperty("api.version", "1.44")
    PostgreSQLContainer(ContainerImages.POSTGRESQL)
        .use { container ->
            container.start()
            val connection = DatabaseConnection(
                container.jdbcUrl.replace("jdbc", "r2dbc"),
                "postgresql",
                container.username,
                container.password
            )
            block(connection)
        }
}

fun testSearchMemory(block: suspend (TopicSearchService) -> Unit) {
    val env = buildMemorySearchEnv()
    val service = buildTopicSearchService(env)
    runBlocking { block(service) }
}

fun testSearchContainer(block: suspend (TopicSearchService) -> Unit) {
    useElasticSearchContainer { env ->
        val service = buildTopicSearchService(env)
        runBlocking { block(service) }
    }
}

fun testOssMemory(block: suspend (ObjectStorageService) -> Unit) {
    val env = buildMemoryOssEnv()
    val service = mediaService(env)
    runBlocking { block(service) }
}

fun testOssContainer(block: suspend (ObjectStorageService) -> Unit) {
    useMinioContainer { env ->
        val service = mediaService(env)
        runBlocking { block(service) }
    }
}

private fun withDatabase(connection: DatabaseConnection, block: suspend (CombinedDatabase) -> Unit) {
    val db = buildExposedDatabase(connection)
    runBlocking {
        db.init()
        try {
            block(db)
        } finally {
            db.clean()
        }
    }
}

fun testDatabaseMemory(block: suspend (CombinedDatabase) -> Unit) {
    val connection = buildMemoryDatabaseConnection()
    withDatabase(connection, block)
}

fun testDatabaseContainer(block: suspend (CombinedDatabase) -> Unit) {
    usePostgresContainer { connection ->
        withDatabase(connection, block)
    }
}
