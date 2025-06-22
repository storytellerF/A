package com.storyteller_f.backend.service

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.*
import com.storyteller_f.a.backend.service.service.BackendConfig
import com.storyteller_f.a.exposed.ExposedDatabaseSession
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.a.exposed.query.batchCreateCommunityRooms
import com.storyteller_f.a.exposed.tables.CommunityRawResult
import com.storyteller_f.a.exposed.tables.Media
import com.storyteller_f.a.exposed.tables.Room
import com.storyteller_f.a.exposed.tables.RoomRawResult
import com.storyteller_f.a.exposed.tables.Title
import com.storyteller_f.a.exposed.tables.Topic
import com.storyteller_f.a.exposed.tables.User
import com.storyteller_f.a.exposed.tables.UserRawResult
import com.storyteller_f.a.exposed.tables.toCommunityIfo
import com.storyteller_f.a.exposed.tables.toMediaInfo
import com.storyteller_f.a.exposed.tables.toRoomInfo
import com.storyteller_f.a.exposed.tables.toTitleInfo
import com.storyteller_f.a.exposed.tables.toTopicInfo
import com.storyteller_f.a.exposed.tables.toUserInfo
import com.storyteller_f.backend.service.index.ElasticTopicSearchService
import com.storyteller_f.backend.service.index.LuceneTopicSearchService
import com.storyteller_f.backend.service.index.TopicSearchService
import com.storyteller_f.backend.service.media.FileSystemMediaService
import com.storyteller_f.backend.service.media.MediaService
import com.storyteller_f.backend.service.media.MinIoMediaService
import com.storyteller_f.backend.service.naming.NameService
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import org.apache.tika.Tika
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.*

class Backend(
    val config: Config,
    val snapshotVerify: Pair<String, String>?,
    val topicSearchService: TopicSearchService,
    val mediaService: MediaService,
    val nameService: NameService,
    val database: Database,
    val databaseSession: ExposedDatabaseSession,
    val exposedDatabase: com.storyteller_f.a.exposed.Database<User>,
) {
    val json by lazy {
        Json {}
    }
    val tika by lazy {
        Tika()
    }
}

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

fun readResourceEnv(resName: String): Map<String, String>? {
    Napier.i {
        "read env from resource: $resName"
    }
    return ClassLoader.getSystemClassLoader().getResourceAsStream(resName)?.use {
        Properties().apply {
            load(it)
        }
    }?.map {
        it.key as String to it.value as String
    }?.associate { it }
}

fun readFileEnv(resName: String): Map<String, String> {
    val file = File(resName)
    Napier.i {
        "read env from file: ${file.canonicalPath}"
    }
    return if (file.exists()) {
        FileInputStream(resName).use {
            Properties().apply {
                load(it)
            }
        }.map {
            it.key as String to it.value as String
        }.associate { it }
    } else {
        emptyMap()
    }
}

fun mediaService(env: MergedEnv): MediaService {
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

fun topicDocumentService(
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

fun databaseConnection(env: MergedEnv): DatabaseConnection {
    val uri = env["DATABASE_URI"]
    val driver = env["DATABASE_DRIVER"]
    val user = env["DATABASE_USER"]
    val pass = env["DATABASE_PASS"]
    return DatabaseConnection(uri, driver, user, pass)
}

suspend fun Backend.uploadFiles(uploadPacks: List<UploadPack>): Result<List<MediaInfo?>> {
    val data = uploadPacks.mapIndexed { i, e ->
        SnowflakeFactory.nextId() to e
    }
    return databaseSession.dbQuery {
        Media.insertMedia(data)
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
                uploadPack.ownerType,
                e.lastModified,
                uploadPack.dimension
            )
        }
    }
}

suspend fun Backend.insertTitleAndTopicDescription(
    title: Title,
    topic: Topic
): Result<TitleInfo> {
    return databaseSession.dbQuery {
        Title.insertTitle(title, topic)
        title.toTitleInfo()
    }
}

suspend fun Backend.getUserInfo(
    fetch: ObjectFetch
): Result<UserInfo?> {
    return exposedDatabase.userDatabase.getUserRawResult(fetch).mapResultIfNotNull {
        processUserRawResultToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}

suspend fun Backend.getUserInfoList(
    listFetch: ObjectListFetch
): Result<List<UserInfo>?> {
    return exposedDatabase.userDatabase.getUserRawResultList(listFetch).mapResult {
        processUserRawResultToUserInfo(it)
    }
}

suspend fun Backend.savePlainTopic(
    topic: Topic,
    content: TopicContent.Plain
) = databaseSession.dbQuery {
    Topic.new(topic)
    exposedDatabase.userDatabase.insertMediaRefs(
        topic.id,
        ObjectType.TOPIC,
        extractMarkdownMediaLink(content.plain).map {
            topic.author to it
        }
    ).getOrThrow()
    topic.toTopicInfo(content = content).copy(content = content)
}

suspend fun Backend.copyMedia(
    media: Media,
    newOwner: PrimaryKey,
    newName: String
): Result<ServerResponse<MediaInfo>> {
    val id = SnowflakeFactory.nextId()
    return databaseSession.dbQuery {
        Media.insertCopiedMedia(id, media, newOwner)
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
                    ObjectType.USER,
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
): Result<PaginationResult<MediaInfo>> =
    exposedDatabase.userDatabase.getMediaPaginationList(uid, primaryKeyFetch).mapResult { (list, count) ->
        processMediaToMediaInfo(list).map {
            PaginationResult(it, count)
        }
    }

suspend fun Backend.processCommunityRawResultToCommunityInfo(
    list: List<CommunityRawResult>
): Result<List<CommunityInfo>?> {
    return exposedDatabase.userDatabase.getMediaByIds(list.flatMap { (community) ->
        listOf(community.iconId, community.posterId, community.fontId)
    }.filterNotNull()).mapResultIfNotNull { medias ->
        processMediaToMediaInfo(medias).map {
            val map = it.associateBy { it.id }
            list.mapIndexed { i, rawResult ->
                rawResult.community.toCommunityIfo().copy(
                    memberCount = rawResult.memberCount,
                    icon = rawResult.community.iconId?.let { map[it] },
                    poster = rawResult.community.posterId?.let { map[it] },
                    hasPoster = rawResult.community.posterId != null,
                    joinedTime = rawResult.joinedTime,
                    lastRead = rawResult.lastRead,
                    font = rawResult.community.fontId?.let { map[it] }
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
    return exposedDatabase.userDatabase.getMemberPaginationResult(objectId, word, primaryKeyFetch)
        .mapResult { (pairs, count) ->
            processUserRawResultToUserInfo(pairs).mapIfNotNull {
                PaginationResult(it, count)
            }
        }
}

suspend fun Backend.getMediaInfoList(
    owner: PrimaryKey,
): Result<List<MediaInfo?>?> {
    return exposedDatabase.userDatabase.getMediaListByOwner(owner).mapResultIfNotNull { medias ->
        processMediaToMediaInfo(medias)
    }
}

suspend fun Backend.searchRoomPaginationResult(
    uid: PrimaryKey?,
    word: String?,
    community: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch,
    search: JoinSearch
): Result<PaginationResult<RoomInfo>?> {
    return exposedDatabase.roomData.getRoomPaginationResult(
        uid,
        word,
        community,
        primaryKeyFetch,
        search
    ).mapResult { (list, count) ->
        processRoomRawResultToRoomInfo(list).mapIfNotNull { value ->
            PaginationResult(value, count)
        }
    }
}

suspend fun Backend.getMediaInfoList(names: List<String>): Result<List<MediaInfo?>?> {
    return exposedDatabase.userDatabase.getMediaByNames(names).mapResult { medias ->
        processMediaToMediaInfo(medias)
    }
}

suspend fun Backend.processMediaToMediaInfo(
    medias: List<Media>,
): Result<List<MediaInfo>> {
    return mediaService.get(AMEDIA_DEFAULT_BUCKET, medias.map {
        it.fullName
    }).map {
        val mediaRecordMap = it.associateBy { it.fullName }
        medias.map { media ->
            mediaRecordMap[media.fullName]!!.let {
                media.toMediaInfo(it.url, it.lastModified)
            }
        }
    }
}

suspend fun Backend.processUserRawResultToUserInfo(
    rawResults: List<UserRawResult<User>>
) = exposedDatabase.userDatabase.getMediaByIds(rawResults.mapNotNull {
    it.user.icon
}).mapResultIfNotNull { medias ->
    processMediaToMediaInfo(medias).map {
        val mediaInfoMap = it.associateBy { it.id }
        rawResults.map { pair ->
            pair.user.toUserInfo().copy(avatar = pair.user.icon?.let { mediaInfoMap[it] })
        }
    }
}

suspend fun Backend.processRoomRawResultToRoomInfo(list: List<RoomRawResult>): Result<List<RoomInfo>?> {
    return exposedDatabase.userDatabase.getMediaByIds(list.mapNotNull {
        it.room.icon
    }).mapResultIfNotNull { medias ->
        processMediaToMediaInfo(medias).map {
            val mediaInfoMap = it.associateBy { it.id }
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
    return exposedDatabase.userDatabase.getUserAlternatUserRawResultList(uid).mapResult {
        processUserRawResultToUserInfo(it)
    }
}

suspend fun createCommunityRoomsRaw(
    id: PrimaryKey,
    ownerUid: PrimaryKey,
    communityAid: String
) {
    batchCreateCommunityRooms(
        listOf(
            "${communityAid}_general" to "General",
            "${communityAid}_lobby" to "Lobby",
            "${communityAid}_support" to "Support"
        ).map { pair ->
            Room(SnowflakeFactory.nextId(), now(), pair.first, pair.second, ownerUid, communityId = id)
        }
    )
}
