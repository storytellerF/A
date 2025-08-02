package com.storyteller_f.a.backend.service

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.*
import com.storyteller_f.a.backend.exposed.query.PaginationResult
import com.storyteller_f.a.backend.exposed.query.batchCreateCommunityRooms
import com.storyteller_f.a.backend.exposed.tables.*
import com.storyteller_f.a.backend.service.index.ElasticTopicSearchService
import com.storyteller_f.a.backend.service.index.LuceneTopicSearchService
import com.storyteller_f.a.backend.service.index.TopicSearchService
import com.storyteller_f.a.backend.service.media.FileSystemMediaService
import com.storyteller_f.a.backend.service.media.MediaService
import com.storyteller_f.a.backend.service.media.MinIoMediaService
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import io.github.aakira.napier.Napier
import org.apache.tika.Tika
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.*

class Backend(
    val customConfig: CustomConfig,
    val topicSearchService: TopicSearchService,
    val mediaService: MediaService,
    val nameService: NameService,
    val exposedDatabase: com.storyteller_f.a.backend.exposed.CombinedDatabase<User>,
) {
    val tika by lazy {
        Tika()
    }
}

class MergedEnv(val list: List<Map<String, String>>) {
    operator fun get(key: String): String? {
        return list.firstNotNullOfOrNull { map ->
            map[key]
        }
    }
}

fun readEnv(envMap: Map<String, String>? = null) = MergedEnv(
    listOfNotNull(
        envMap, // 测试时手动传递
        System.getenv(), // 正式部署
        readFileEnv("../../${BackendConfig.FLAVOR}.env"), // 本地开发
        readFileEnv(".env"), // koyeb 部署
        readResourceEnv(".env"), // 测试
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

fun readFileEnv(resName: String): Map<String, String>? {
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
        null
    }
}

fun mediaService(env: MergedEnv): MediaService {
    return when (env["MEDIA_SERVICE"]) {
        "minio" -> {
            val url = env["MINIO_URL"] ?: throw Exception("MINIO_URL is empty")
            val name = env["MINIO_NAME"] ?: throw Exception("MINIO_NAME is empty")
            val pass = env["MINIO_PASS"] ?: throw Exception("MINIO_PASS is empty")
            MinIoMediaService(MinIoConnection(url, name, pass), env["MINIO_HOST"])
        }

        "filesystem" -> {
            val url = env["SERVER_URL"] ?: throw Exception("SERVER_URL is empty")
            val base = env["FILE_SYSTEM_MEDIA_PATH"]
            val p = if (base.isNullOrBlank()) {
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
            val certFile = env["ELASTIC_CERT_FILE"] ?: throw Exception("ELASTIC_CERT_FILE is empty")
            val url = env["ELASTIC_URL"] ?: throw Exception("ELASTIC_URL is empty")
            val name = env["ELASTIC_NAME"] ?: throw Exception("ELASTIC_NAME is empty")
            val pass = env["ELASTIC_PASSWORD"] ?: throw Exception("ELASTIC_PASSWORD is empty")
            val connection = ElasticConnection(url, certFile, name, pass)
            ElasticTopicSearchService(connection)
        }

        "lucene" -> {
            val luceneBase = env["LUCENE_BASE_PATH"]
            val (path, isInMemory) = if (luceneBase.isNullOrBlank()) {
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
    val uri = env["DATABASE_URI"] ?: throw Exception("DATABASE_URI is empty")
    val driver = env["DATABASE_DRIVER"] ?: throw Exception("DATABASE_DRIVE is empty")
    val user = env["DATABASE_USER"] ?: throw Exception("DATABASE_USER is empty")
    val pass = env["DATABASE_PASS"] ?: throw Exception("DATABASE_PASS is empty")
    return DatabaseConnection(uri, driver, user, pass)
}

suspend fun Backend.uploadFiles(uploadPacks: List<UploadPack>): Result<List<MediaInfo?>> {
    val data = uploadPacks.map {
        val nextId = SnowflakeFactory.nextId()
        Media(
            nextId,
            now(),
            it.name,
            0,
            it.dimension?.width ?: 0,
            it.dimension?.height ?: 0,
            it.owner,
            it.ownerType,
            it.contentType,
            it.size
        )
    }
    return merge({
        mediaService.upload(AMEDIA_DEFAULT_BUCKET, uploadPacks)
    }, {
        exposedDatabase.mediaDatabase.insertMedia(data)
        Result.success(Unit)
    }).map { (mediaRecords) ->
        mediaRecords.mapIndexed { i, e ->
            val uploadPack = uploadPacks[i]
            MediaInfo(
                data[i].id,
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

suspend fun Backend.getUserInfo(
    fetch: ObjectFetch,
): Result<UserInfo?> {
    return exposedDatabase.userDatabase.getRawUser(fetch).mapResultIfNotNull {
        processRawUserToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}

suspend fun Backend.getUserInfoList(
    listFetch: ObjectListFetch,
): Result<List<UserInfo>> {
    return exposedDatabase.userDatabase.getRawUsers(listFetch).mapResult {
        processRawUserToUserInfo(it)
    }
}

suspend fun Backend.copyMedia(
    media: Media,
    newOwner: PrimaryKey,
    newName: String,
): Result<ServerResponse<MediaInfo>> {
    val id = SnowflakeFactory.nextId()
    return merge({
        mediaService.copy(
            AMEDIA_DEFAULT_BUCKET,
            listOf(CopyPack("${media.owner}/${media.name}", newName))
        ).map { list ->
            ServerResponse(list.map {
                val dimension =
                    if (media.width != 0 && media.height != 0) {
                        Dimension(
                            media.width,
                            media.height
                        )
                    } else {
                        null
                    }
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
        }
    }, {
        exposedDatabase.mediaDatabase.insertCopiedMedia(id, media, newOwner)
    }).map {
        it.first
    }
}

suspend fun Backend.getMediaPaginationResult(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<MediaInfo>> =
    exposedDatabase.mediaDatabase.getMediaPaginationList(uid, primaryKeyFetch)
        .mapResult { (list, count) ->
            processMediaToMediaInfo(list).map {
                PaginationResult(it, count)
            }
        }

suspend fun Backend.processRawCommunityToCommunityInfo(
    list: List<RawCommunity>,
): Result<List<CommunityInfo>?> {
    return exposedDatabase.mediaDatabase.getMediaByIds(list.flatMap { (community) ->
        listOf(community.iconId, community.posterId, community.fontId)
    }.filterNotNull()).mapResultIfNotNull { medias ->
        processMediaToMediaInfo(medias).map { mediaList ->
            val map = mediaList.associateBy { it.id }
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
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<UserInfo>?> {
    return exposedDatabase.containerDatabase.getMemberPaginationResult(
        objectId,
        word,
        primaryKeyFetch
    )
        .mapResult { (pairs, count) ->
            processRawUserToUserInfo(pairs).mapIfNotNull {
                PaginationResult(it, count)
            }
        }
}

suspend fun Backend.searchRoomPaginationResult(
    uid: PrimaryKey?,
    word: String?,
    community: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch,
    search: JoinSearch,
): Result<PaginationResult<RoomInfo>?> {
    return exposedDatabase.roomData.getRoomPaginationResult(
        uid,
        word,
        community,
        primaryKeyFetch,
        search
    ).mapResult { (list, count) ->
        processRawRoomToRoomInfo(list).mapIfNotNull { value ->
            PaginationResult(value, count)
        }
    }
}

suspend fun Backend.getMediaInfoList(names: List<String>): Result<List<MediaInfo?>?> {
    return exposedDatabase.mediaDatabase.getMediaByNames(names).mapResult { medias ->
        processMediaToMediaInfo(medias)
    }
}

suspend fun Backend.processMediaToMediaInfo(
    medias: List<Media>,
): Result<List<MediaInfo>> {
    return mediaService.get(AMEDIA_DEFAULT_BUCKET, medias.map {
        it.fullName
    }).map { mediaList ->
        val mediaRecordMap = mediaList.associateBy { it.fullName }
        medias.map { media ->
            mediaRecordMap[media.fullName]!!.let {
                media.toMediaInfo(it.url, it.lastModified)
            }
        }
    }
}

suspend fun Backend.processRawUserToUserInfo(
    rawResults: List<RawUser<User>>,
) = exposedDatabase.mediaDatabase.getMediaByIds(rawResults.mapNotNull {
    it.user.icon
}).mapResult { medias ->
    processMediaToMediaInfo(medias).map { list ->
        val mediaInfoMap = list.associateBy { it.id }
        rawResults.map { pair ->
            pair.user.toUserInfo().copy(avatar = pair.user.icon?.let { mediaInfoMap[it] })
        }
    }
}

suspend fun Backend.processRawRoomToRoomInfo(list: List<RawRoom>): Result<List<RoomInfo>?> {
    return exposedDatabase.mediaDatabase.getMediaByIds(list.mapNotNull {
        it.room.icon
    }).mapResultIfNotNull { medias ->
        processMediaToMediaInfo(medias).map { mediaList ->
            val mediaInfoMap = mediaList.associateBy { it.id }
            list.map { rawRoom ->
                rawRoom.room.toRoomInfo()
                    .copy(
                        icon = rawRoom.room.icon?.let { mediaInfoMap[it] },
                        joinedTime = rawRoom.joinedTime,
                        lastRead = rawRoom.topicId,
                        memberCount = rawRoom.memberCount
                    )
            }
        }
    }
}

suspend fun Backend.getUserAlternateUserInfoList(
    uid: PrimaryKey,
    fetch: PrimaryKeyFetch,
): Result<PaginationResult<AlternativeAccountInfo>?> {
    return exposedDatabase.userDatabase.getRawAlternativePaginationListByHost(
        uid,
        fetch
    ).mapResult { (results, total) ->
        processRawUserToUserInfo(results.map {
            it.rawUser
        }).mapIfNotNull { userList ->
            val map = userList.associateBy { it.id }
            PaginationResult(results.mapNotNull {
                map[it.rawUser.user.id]?.let { userInfo ->
                    AlternativeAccountInfo(
                        it.rawUser.user.id,
                        it.alternateAccount.privateKey,
                        userInfo
                    )
                }
            }, total)
        }
    }
}

suspend fun createCommunityRoomsRaw(
    id: PrimaryKey,
    ownerUid: PrimaryKey,
    communityAid: String,
) {
    batchCreateCommunityRooms(
        listOf(
            "${communityAid}_general" to "General",
            "${communityAid}_lobby" to "Lobby",
            "${communityAid}_support" to "Support"
        ).map { pair ->
            Room(
                SnowflakeFactory.nextId(),
                now(),
                pair.first,
                pair.second,
                ownerUid,
                communityId = id
            )
        }
    )
}

suspend fun Backend.isKeyVerified(
    roomId: PrimaryKey,
    encryptedAes: Map<PrimaryKey, String>,
): Result<Boolean> {
    return exposedDatabase.containerDatabase.getJoinedUserList(roomId).map { value ->
        value.map {
            it.uid
        }.toSet().minus(encryptedAes.keys).isEmpty()
    }
}
