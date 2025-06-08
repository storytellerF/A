package com.storyteller_f

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.storyteller_f.backend.BackendConfig
import com.storyteller_f.index.ElasticTopicSearchService
import com.storyteller_f.index.LuceneTopicSearchService
import com.storyteller_f.index.TopicSearchService
import com.storyteller_f.media.FileSystemMediaService
import com.storyteller_f.media.MediaService
import com.storyteller_f.media.MinIoMediaService
import com.storyteller_f.naming.NameService
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.types.Cursor
import com.storyteller_f.types.PrimaryKeyFetch
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.*

class Backend(
    val config: Config,
    val snapshotVerify: Pair<String, String>,
    val topicSearchService: TopicSearchService,
    val mediaService: MediaService,
    val nameService: NameService,
    val database: Database,
    val exposedDatabaseSession: ExposedDatabaseSession
) {
    val json = Json {}
}

class Config(
    val databaseConnection: DatabaseConnection,
    val buildType: String,
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
    return MergedEnv(
        listOf(
            map,
            System.getenv(),
            readFileEnv("../${BackendConfig.FLAVOR}.env"),
            readFileEnv(".env"),
            readResourceEnv(".env"),
        )
    )
}

fun readResourceEnv(resName: String) = ClassLoader.getSystemClassLoader().getResourceAsStream(resName)?.use {
    Properties().apply {
        load(it)
    }
}?.map {
    it.key as String to it.value as String
}?.associate { it }

fun readFileEnv(resName: String) = (if (File(resName).exists()) {
    FileInputStream(resName).use {
        Properties().apply {
            load(it)
        }
    }.map {
        it.key as String to it.value as String
    }.associate { it }
} else {
    emptyMap()
})

fun buildBackendFromEnv(env: MergedEnv): Backend {
    println("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"]
    val flavor = env["FLAVOR"]

    val config = Config(databaseConnection, buildType, flavor)

    val topicDocumentService = topicDocumentService(env)
    val mediaService = mediaService(env)

    val database = DatabaseFactory.connect(databaseConnection)
    return Backend(
        config,
        env["SNAPSHOT_KEYSTORE_PATH"] to env["SNAPSHOT_KEY_PASS"],
        topicDocumentService,
        mediaService,
        NameService(),
        database,
        ExposedDatabaseSession(database, buildType)
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
            val p = if (base.isBlank()) {
                Napier.i {
                    "use in-memory amedia"
                }
                MemoryFileSystemBuilder.newLinux().build().getPath("/amedia")
            } else {
                Paths.get(base)
            }
            FileSystemMediaService(url, p)
        }

        else -> throw UnsupportedOperationException("unsupported media service type ${env["MEDIA_SERVICE"]}")
    }
}

private fun topicDocumentService(
    env: MergedEnv,
): TopicSearchService {
    return when (val type = env["SEARCH_SERVICE"]) {
        "elastic" -> {
            val certFile = env["ELASTIC_CERT_FILE"]
            val url = env["ELASTIC_URL"]
            val name = env["ELASTIC_NAME"]
            val pass = env["ELASTIC_PASSWORD"]
            ElasticTopicSearchService(ElasticConnection(url, certFile, name, pass))
        }

        "lucene" -> {
            val luceneBase = env["LUCENE_BASE_PATH"]
            val (path, isInMemory) = if (luceneBase.isBlank()) {
                Napier.i {
                    "use in-memory document service"
                }
                MemoryFileSystemBuilder.newLinux().build().getPath("/documents") to true
            } else {
                Paths.get(luceneBase) to false
            }
            LuceneTopicSearchService(path, isInMemory)
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
    primaryKeyFetch: PrimaryKeyFetch
): Query {
    val cursor = primaryKeyFetch.cursor
    val order = when (cursor) {
        is Cursor.NextCursor<PrimaryKey> -> {
            andWhere {
                table.id less cursor.value
            }
            SortOrder.DESC
        }

        is Cursor.PreCursor<PrimaryKey> -> {
            andWhere {
                table.id greater cursor.value
            }
            SortOrder.ASC
        }

        null -> null
    }
    return orderBy(table.id, order ?: SortOrder.DESC).limit(primaryKeyFetch.size)
}

class ForbiddenException(message: String = "Invalid operation") : Exception(message)
class CustomBadRequestException(message: String) : Exception(message)

sealed interface ObjectFetch {
    data class AidFetch(val aid: String) : ObjectFetch
    data class IdFetch(val id: PrimaryKey) : ObjectFetch
}

sealed interface ObjectListFetch {
    data class AidListFetch(val aidList: List<String>) : ObjectListFetch
    data class IdListFetch(val idList: List<PrimaryKey>) : ObjectListFetch
}
