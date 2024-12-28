package com.storyteller_f

import com.storyteller_f.index.ElasticTopicSearchService
import com.storyteller_f.index.LuceneTopicSearchService
import com.storyteller_f.index.TopicSearchService
import com.storyteller_f.media.FileSystemMediaService
import com.storyteller_f.media.MediaService
import com.storyteller_f.media.MinIoMediaService
import com.storyteller_f.naming.NameService
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import java.nio.file.Paths
import java.util.*

class Backend(
    val config: Config,
    val topicSearchService: TopicSearchService,
    val mediaService: MediaService,
    val nameService: NameService
)

class Config(
    val databaseConnection: DatabaseConnection,
    val isProd: Boolean,
    val flavor: String
)

data class ElasticConnection(val url: String, val certFile: String, val name: String, val pass: String)
data class MinIoConnection(val url: String, val user: String, val pass: String)
data class DatabaseConnection(val uri: String, val driver: String, val user: String, val password: String)

class MergedEnv(val list: List<Map<String, String>?>) {
    operator fun get(key: String): String {
        return list.firstNotNullOfOrNull { map ->
            map?.get(key)
        } ?: ""
    }
}

fun readEnv(map: Map<String, String> = emptyMap()): MergedEnv {
    return MergedEnv(listOf(map, readResourceEnv(".env"), System.getenv()))
}

fun readResourceEnv(resName: String) = ClassLoader.getSystemClassLoader().getResourceAsStream(resName)?.use {
    Properties().apply {
        load(it)
    }
}?.map {
    it.key as String to it.value as String
}?.associate { it }

fun buildBackendFromEnv(env: MergedEnv): Backend {
    println("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val isProd = env["IS_PROD"].toBoolean()
    val flavor = env["FLAVOR"]

    val config = Config(databaseConnection, isProd, flavor)

    val topicDocumentService = topicDocumentService(env)
    val mediaService = mediaService(env)

    return Backend(
        config,
        topicDocumentService,
        mediaService,
        NameService()
    )
}

private fun mediaService(env: MergedEnv): MediaService {
    return when (env["MEDIA_SERVICE"]) {
        "minio" -> {
            val url = env["MINIO_URL"]
            val name = env["MINIO_NAME"]
            val pass = env["MINIO_PASS"]
            MinIoMediaService(MinIoConnection(url, name, pass))
        }

        "filesystem" -> {
            val url = env["SERVER_URL"]
            val base = env["FILE_SYSTEM_MEDIA_PATH"]
            FileSystemMediaService(url, base)
        }

        else -> throw UnsupportedOperationException("unsupported media service type ${env["MEDIA_SERVICE"]}")
    }
}

private fun topicDocumentService(
    env: MergedEnv,
): TopicSearchService {
    return when (val type = env["SEARCH_SERVICE"]) {
        "elastic" -> {
            val certFile = env["CERT_FILE"]
            val url = env["ELASTIC_URL"]
            val name = env["ELASTIC_NAME"]
            val pass = env["ELASTIC_PASSWORD"]
            ElasticTopicSearchService(ElasticConnection(url, certFile, name, pass))
        }

        "lucene" -> {
            val luceneBase = env["LUCENE_BASE_PATH"]
            val path = Paths.get(luceneBase.removeSurrounding("'"), "index")
            Napier.i {
                "lucene path $path"
            }
            LuceneTopicSearchService(path)
        }

        else -> throw UnsupportedOperationException("unsupported search service type [$type]")
    }
}

private fun databaseConnection(env: MergedEnv): DatabaseConnection {
    val uri = env["DATABASE_URI"]
    val driver = env["DATABASE_DRIVER"]
    val user = env["DATABASE_USER"]
    val pass = env["DATABASE_PASS"]
    return DatabaseConnection(uri, driver, user, pass)
}

fun Query.bindPaginationQuery(
    table: BaseTable,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int
): Query {
    if (nextPageToken != null) {
        andWhere {
            table.id less nextPageToken
        }
    } else if (prePageToken != null) {
        andWhere {
            table.id greater prePageToken
        }
    }
    return orderBy(table.id, SortOrder.DESC).limit(size)
}

class UnauthorizedException : Exception()
class ForbiddenException(message: String = "Invalid operation") : Exception(message)
class CustomBadRequestException(message: String) : Exception(message)
