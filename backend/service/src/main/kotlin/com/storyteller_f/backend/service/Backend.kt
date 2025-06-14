package com.storyteller_f.backend.service

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.service.service.BackendConfig
import com.storyteller_f.backend.service.index.ElasticTopicSearchService
import com.storyteller_f.backend.service.index.LuceneTopicSearchService
import com.storyteller_f.backend.service.index.TopicDocument
import com.storyteller_f.backend.service.index.TopicSearchService
import com.storyteller_f.backend.service.media.CopyPack
import com.storyteller_f.backend.service.media.FileSystemMediaService
import com.storyteller_f.backend.service.media.MediaService
import com.storyteller_f.backend.service.media.MinIoMediaService
import com.storyteller_f.backend.service.media.UploadPack
import com.storyteller_f.backend.service.naming.NameService
import com.storyteller_f.backend.service.query.getMediaByNames
import com.storyteller_f.backend.service.query.getMediaListByOwner
import com.storyteller_f.backend.service.query.getMediaPaginationList
import com.storyteller_f.backend.service.query.getMemberPaginationResult
import com.storyteller_f.backend.service.query.getRoomPaginationResult
import com.storyteller_f.backend.service.query.getUserAlternatUserRawResultList
import com.storyteller_f.backend.service.query.getUserRawResult
import com.storyteller_f.backend.service.query.getUserRawResultList
import com.storyteller_f.backend.service.query.insertCopiedMedia
import com.storyteller_f.backend.service.query.insertMedia
import com.storyteller_f.backend.service.query.insertMediaRefs
import com.storyteller_f.backend.service.query.insertTitle
import com.storyteller_f.backend.service.tables.CommunityRawResult
import com.storyteller_f.backend.service.tables.Media
import com.storyteller_f.backend.service.tables.RoomRawResult
import com.storyteller_f.backend.service.tables.Title
import com.storyteller_f.backend.service.tables.Topic
import com.storyteller_f.backend.service.tables.UserRawResult
import com.storyteller_f.backend.service.tables.toCommunityIfo
import com.storyteller_f.backend.service.tables.toMediaInfo
import com.storyteller_f.backend.service.tables.toRoomInfo
import com.storyteller_f.backend.service.tables.toTitleInfo
import com.storyteller_f.backend.service.tables.toTopicInfo
import com.storyteller_f.backend.service.tables.toUserInfo
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
import org.jetbrains.exposed.sql.*
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.*
import kotlin.collections.get
import kotlin.collections.map

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
    return orderBy(table.id to (order ?: SortOrder.DESC)).limit(primaryKeyFetch.size)
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
    }.map { urls ->
        urls.mapIndexed { i, e ->
            if (e != null) {
                val uploadPack = uploadPacks[i]
                MediaInfo(
                    data[i].first,
                    e.first,
                    uploadPack.newFullName,
                    uploadPack.contentType,
                    uploadPack.size,
                    uploadPack.name,
                    uploadPack.owner,
                    e.second,
                    uploadPack.dimension
                )
            } else {
                null
            }
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

suspend fun Backend.getUserInfoAndRelatedMedia(
    fetch: ObjectFetch
): Result<UserInfo?> {
    return databaseSession.getUserRawResult(fetch).mapResultIfNotNull {
        processUserRawResultToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}

suspend fun Backend.getUsersInfoByIds(
    listFetch: ObjectListFetch.IdListFetch
): Result<List<UserInfo>?> {
    return databaseSession.getUserRawResultList(listFetch).mapResult {
        processUserRawResultToUserInfo(it)
    }
}

suspend fun Backend.savePlainTopic(
    topic: Topic,
    content: TopicContent.Plain
) = databaseSession.dbQuery {
    Topic.Companion.new(topic)
    databaseSession.insertMediaRefs(topic.id, ObjectType.TOPIC, extractMarkdownMediaLink(content.plain).map {
        topic.author to it
    }).getOrThrow()

    topicSearchService.saveDocument(
        listOf(TopicDocument.Companion.fromTopic(topic, content))
    ).getOrThrow()
    topic.toTopicInfo().copy(content = content)
}

suspend fun Backend.copyMedia(
    media: Media,
    newOwner: PrimaryKey,
    newName: String
): Result<ServerResponse<MediaInfo?>> {
    val id = SnowflakeFactory.nextId()
    return databaseSession.dbQuery {
        insertCopiedMedia(id, media, newOwner)
        mediaService.copy(
            AMEDIA_DEFAULT_BUCKET,
            listOf(CopyPack("${media.owner}/${media.name}", newName))
        ).map { list ->
            ServerResponse(list.map {
                if (it != null) {
                    val dimension =
                        if (media.width != 0 && media.height != 0) Dimension(media.width, media.height) else null
                    MediaInfo(
                        id,
                        it.first,
                        newName,
                        media.contentType,
                        media.size,
                        media.name,
                        newOwner,
                        it.second,
                        dimension
                    )
                } else {
                    null
                }
            }, null)
        }.getOrThrow()
    }
}

suspend fun Backend.getMediaPaginationResult(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<MediaInfo>> =
    databaseSession.getMediaPaginationList(uid, primaryKeyFetch).mapResult { (list, count) ->
        val names = list.map {
            "${it.owner}/${it.name}"
        }
        mediaService.get(AMEDIA_DEFAULT_BUCKET, names).map { mediaUrls ->
            val data = mediaUrls.mapIndexedNotNull { i, e ->
                if (e != null) {
                    val media = list[i]
                    val dimension = if (media.contentType.startsWith("image")) {
                        Dimension(media.width, media.height)
                    } else {
                        null
                    }
                    MediaInfo(
                        media.id,
                        e.first,
                        names[i],
                        media.contentType,
                        media.size,
                        media.name,
                        media.owner,
                        e.second,
                        dimension
                    )
                } else {
                    null
                }
            }
            PaginationResult(data, count)
        }
    }

suspend fun Backend.processCommunityRawResultToCommunityInfo(
    list: List<CommunityRawResult>
): Result<List<CommunityInfo>?> {
    return getMediaInfoList(list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    }).mapIfNotNull { icons ->
        list.mapIndexed { i, communityPair ->
            val first = icons[i * 2]
            val second = icons[i * 2 + 1]
            communityPair.communityInfo.toCommunityIfo().copy(
                memberCount = communityPair.memberCount,
                icon = first,
                poster = second,
                hasPoster = second != null,
                joinedTime = communityPair.joinedTime,
                lastRead = communityPair.lastRead
            )
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
    return this.databaseSession.getMediaListByOwner(owner).mapResultIfNotNull { medias ->
        mediaService.get(AMEDIA_DEFAULT_BUCKET, medias.map {
            it.newFullName
        }).mapIfNotNull {
            medias.mapIndexed { i, e ->
                it[i]?.let { it1 -> e.toMediaInfo(it1) }
            }
        }
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

suspend fun Backend.getMediaInfoList(names: List<String?>): Result<List<MediaInfo?>?> {
    return this.databaseSession.getMediaByNames(names).mapResult { medias ->
        val mediaMap = medias.filterNotNull().associateBy { it.newFullName }
        mediaService.get(AMEDIA_DEFAULT_BUCKET, names.map {
            mediaMap[it]?.newFullName
        }).map {
            names.mapIndexed { i, e ->
                if (e != null) {
                    it[i]?.let { it1 -> mediaMap[e]?.toMediaInfo(it1) }
                } else {
                    null
                }
            }
        }
    }
}

suspend fun Backend.processUserRawResultToUserInfo(
    rawResults: List<UserRawResult>
) = getMediaInfoList(rawResults.map {
    it.avatar
}).mapIfNotNull { value ->
    rawResults.mapIndexed { index, pair ->
        pair.user.toUserInfo().copy(avatar = value[index])
    }
}

suspend fun Backend.processRoomRawResultToRoomInfo(list: List<RoomRawResult>): Result<List<RoomInfo>?> {
    return getMediaInfoList(list.map {
        it.icon
    }).mapIfNotNull { icons ->
        list.mapIndexed { i, roomRawResult ->
            roomRawResult.roomInfo.toRoomInfo()
                .copy(
                    icon = icons[i],
                    joinedTime = roomRawResult.joinedTime,
                    lastRead = roomRawResult.topicId,
                    memberCount = roomRawResult.memberCount
                )
        }
    }
}

suspend fun Backend.getUserAlternateUserInfoList(uid: PrimaryKey): Result<List<UserInfo>?> {
    return databaseSession.getUserAlternatUserRawResultList(uid).mapResult {
        processUserRawResultToUserInfo(it)
    }
}
