package com.storyteller_f.backend.service

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.service.service.BackendConfig
import com.storyteller_f.backend.service.index.ElasticTopicSearchService
import com.storyteller_f.backend.service.index.LuceneTopicSearchService
import com.storyteller_f.backend.service.index.TopicDocument
import com.storyteller_f.backend.service.index.TopicSearchService
import com.storyteller_f.backend.service.media.*
import com.storyteller_f.backend.service.naming.NameService
import com.storyteller_f.backend.service.query.*
import com.storyteller_f.backend.service.tables.*
import com.storyteller_f.backend.service.types.Cursor
import com.storyteller_f.backend.service.types.PaginationResult
import com.storyteller_f.backend.service.types.PrimaryKeyFetch
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
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
    val databaseSession: ExposedDatabaseSession
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

fun readEnv(envMap: Map<String, String> = emptyMap()) = MergedEnv(
    listOf(
        envMap,
        System.getenv(),
        readFileEnv("../../${BackendConfig.FLAVOR}.env"),
        readFileEnv(".env"),
        readResourceEnv(".env"),
    )
)

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

suspend fun Backend.uploadFiles(uploadPacks: List<UploadPack>): Result<List<MediaInfo?>> {
    val data = uploadPacks.mapIndexed { i, e ->
        SnowflakeFactory.nextId() to e
    }
    return databaseSession.dbQuery {
        insertMedia(data)
        mediaService.upload(AMEDIA_DEFAULT_BUCKET, uploadPacks).getOrThrow()
    }.map { mediaRecords ->
        mediaRecords.mapIndexed { i, e ->
            val uploadPack = uploadPacks[i]
            MediaInfo(
                data[i].first,
                e.url,
                uploadPack.newFullName,
                uploadPack.contentType,
                uploadPack.size,
                uploadPack.name,
                uploadPack.owner,
                e.lastModified,
                uploadPack.dimension
            )
        }
    }
}

suspend fun Backend.insertTitleAndTopicDescription(
    title: Title,
    topic: Topic,
    description: String
): Result<TitleInfo> {
    return databaseSession.dbQuery {
        insertTitle(title, topic)
        topicSearchService.saveDocument(
            listOf(TopicDocument.Companion.fromTopic(topic, TopicContent.Plain(description)))
        ).getOrThrow()
        title.toTitleInfo()
    }
}

suspend fun Backend.getUserInfo(
    fetch: ObjectFetch
): Result<UserInfo?> {
    return databaseSession.getUserRawResult(fetch).mapResultIfNotNull {
        processUserRawResultToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}

suspend fun Backend.getUserInfoList(
    listFetch: ObjectListFetch
): Result<List<UserInfo>?> {
    return databaseSession.getUserRawResultList(listFetch).mapResult {
        processUserRawResultToUserInfo(it)
    }
}

suspend fun Backend.savePlainTopic(
    topic: Topic,
    content: TopicContent.Plain
) = databaseSession.dbQuery {
    Topic.new(topic)
    databaseSession.insertMediaRefs(topic.id, ObjectType.TOPIC, extractMarkdownMediaLink(content.plain).map {
        topic.author to it
    }).getOrThrow()
    topic.toTopicInfo(content = content).copy(content = content)
}

suspend fun Backend.copyMedia(
    media: Media,
    newOwner: PrimaryKey,
    newName: String
): Result<ServerResponse<MediaInfo>> {
    val id = SnowflakeFactory.nextId()
    return databaseSession.dbQuery {
        insertCopiedMedia(id, media, newOwner)
        mediaService.copy(
            AMEDIA_DEFAULT_BUCKET,
            listOf(CopyPack("${media.owner}/${media.name}", newName))
        ).map { list ->
            ServerResponse(list.map {
                val dimension =
                    if (media.width != 0 && media.height != 0) Dimension(media.width, media.height) else null
                MediaInfo(
                    id,
                    it.url,
                    newName,
                    media.contentType,
                    media.size,
                    media.name,
                    newOwner,
                    it.lastModified,
                    dimension
                )
            }, null)
        }.getOrThrow()
    }
}

suspend fun Backend.getMediaPaginationResult(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<MediaInfo?>> =
    databaseSession.getMediaPaginationList(uid, primaryKeyFetch).mapResult { (list, count) ->
        processMediaToMediaInfo(list).map {
            PaginationResult(it, count)
        }
    }

suspend fun Backend.processCommunityRawResultToCommunityInfo(
    list: List<CommunityRawResult>
): Result<List<CommunityInfo>?> {
    return databaseSession.getMediaByIds(list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    }.filterNotNull()).mapResultIfNotNull { icons ->
        processMediaToMediaInfo(icons).map {
            val map = it.filterNotNull().associateBy { it.id }
            list.mapIndexed { i, rawResult ->
                rawResult.community.toCommunityIfo().copy(
                    memberCount = rawResult.memberCount,
                    icon = rawResult.icon?.let { map[it] },
                    poster = rawResult.poster?.let { map[it] },
                    hasPoster = rawResult.poster != null,
                    joinedTime = rawResult.joinedTime,
                    lastRead = rawResult.lastRead
                )
            }
        }
    }
}

suspend fun Backend.searchMembers(
    objectId: PrimaryKey?,
    word: String?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<UserInfo>?> {
    return databaseSession.getMemberPaginationResult(objectId, word, primaryKeyFetch).mapResult { (pairs, count) ->
        processUserRawResultToUserInfo(pairs).mapIfNotNull {
            PaginationResult(it, count)
        }
    }
}

suspend fun Backend.getMediaInfoList(
    owner: PrimaryKey,
): Result<List<MediaInfo?>?> {
    return databaseSession.getMediaListByOwner(owner).mapResultIfNotNull { medias ->
        processMediaToMediaInfo(medias)
    }
}

suspend fun Backend.searchRoomPaginationResult(
    uid: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    community: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<RoomInfo>?> {
    return databaseSession.getRoomPaginationResult(
        uid,
        joinStatusSearch,
        word,
        community,
        primaryKeyFetch
    ).mapResult { (list, count) ->
        processRoomRawResultToRoomInfo(list).mapIfNotNull { value ->
            PaginationResult(value, count)
        }
    }
}

suspend fun Backend.getMediaInfoList(names: List<String>): Result<List<MediaInfo?>?> {
    return databaseSession.getMediaByNames(names).mapResult { medias ->
        processMediaToMediaInfo(medias)
    }
}

private suspend fun Backend.processMediaToMediaInfo(
    medias: List<Media>,
): Result<List<MediaInfo?>> {
    return mediaService.get(AMEDIA_DEFAULT_BUCKET, medias.map {
        it.fullName
    }).map {
        val mediaRecordMap = it.associateBy { it.fullName }
        medias.map { media ->
            mediaRecordMap[media.fullName]?.let {
                media.toMediaInfo(it.url, it.lastModified)
            }
        }
    }
}

suspend fun Backend.processUserRawResultToUserInfo(
    rawResults: List<UserRawResult>
) = databaseSession.getMediaByIds(rawResults.mapNotNull {
    it.user.icon
}).mapResultIfNotNull { medias ->
    processMediaToMediaInfo(medias).map {
        val mediaInfoMap = it.filterNotNull().associateBy { it.id }
        rawResults.map { pair ->
            pair.user.toUserInfo().copy(avatar = pair.user.icon?.let { mediaInfoMap[it] })
        }
    }
}

suspend fun Backend.processRoomRawResultToRoomInfo(list: List<RoomRawResult>): Result<List<RoomInfo>?> {
    return databaseSession.getMediaByIds(list.mapNotNull {
        it.room.icon
    }).mapResultIfNotNull { medias ->
        processMediaToMediaInfo(medias).map {
            val mediaInfoMap = it.filterNotNull().associateBy { it.id }
            list.map { roomRawResult ->
                roomRawResult.room.toRoomInfo()
                    .copy(
                        icon = roomRawResult.room.icon?.let { mediaInfoMap[it] },
                        joinedTime = roomRawResult.joinedTime,
                        lastRead = roomRawResult.topicId,
                        memberCount = roomRawResult.memberCount
                    )
            }
        }
    }
}

suspend fun Backend.getUserAlternateUserInfoList(uid: PrimaryKey): Result<List<UserInfo>?> {
    return databaseSession.getUserAlternatUserRawResultList(uid).mapResult {
        processUserRawResultToUserInfo(it)
    }
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
    return orderBy(table.id to (order ?: SortOrder.DESC)).limit(primaryKeyFetch.size)
}
