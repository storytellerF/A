package com.storyteller_f.a.backend.service

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.*
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.core.types.RawCommunity
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.core.types.toCommunityIfo
import com.storyteller_f.a.backend.core.types.toFileInfo
import com.storyteller_f.a.backend.core.types.toQuotaInfo
import com.storyteller_f.a.backend.core.types.toRoomInfo
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.a.backend.exposed.isDup
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.a.backend.service.object_storage.FileSystemObjectStorageService
import com.storyteller_f.a.backend.service.object_storage.MinIoObjectStorageService
import com.storyteller_f.a.backend.service.object_storage.ObjectStorageService
import com.storyteller_f.a.backend.service.search.CommunitySearchService
import com.storyteller_f.a.backend.service.search.RoomSearchService
import com.storyteller_f.a.backend.service.search.TopicSearchService
import com.storyteller_f.a.backend.service.search.UserSearchService
import com.storyteller_f.a.backend.service.search.elastic.ElasticCommunitySearchService
import com.storyteller_f.a.backend.service.search.elastic.ElasticRoomSearchService
import com.storyteller_f.a.backend.service.search.elastic.ElasticTopicSearchService
import com.storyteller_f.a.backend.service.search.elastic.ElasticUserSearchService
import com.storyteller_f.a.backend.service.search.lucene.LuceneCommunitySearchService
import com.storyteller_f.a.backend.service.search.lucene.LuceneRoomSearchService
import com.storyteller_f.a.backend.service.search.lucene.LuceneTopicSearchService
import com.storyteller_f.a.backend.service.search.lucene.LuceneUserSearchService
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.utils.*
import io.github.aakira.napier.Napier
import org.apache.tika.Tika
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class Backend(
    val customConfig: CustomConfig,
    val topicSearchService: TopicSearchService,
    val roomSearchService: RoomSearchService,
    val communitySearchService: CommunitySearchService,
    val userSearchService: UserSearchService,
    val objectStorageService: ObjectStorageService,
    val nameService: NameService,
    val combinedDatabase: CombinedDatabase,
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
    return ClassLoader.getSystemResourceAsStream(resName)?.use {
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

fun mediaService(env: MergedEnv): ObjectStorageService {
    return when (env["MEDIA_SERVICE"]) {
        "minio" -> {
            val url = env["MINIO_URL"] ?: throw Exception("MINIO_URL is empty")
            val name = env["MINIO_NAME"] ?: throw Exception("MINIO_NAME is empty")
            val pass = env["MINIO_PASS"] ?: throw Exception("MINIO_PASS is empty")
            MinIoObjectStorageService(MinIoConnection(url, name, pass), env["MINIO_HOST"])
        }

        "filesystem" -> {
            val url = env["SERVER_URL"] ?: throw Exception("SERVER_URL is empty")
            val base = env["FILE_SYSTEM_MEDIA_PATH"]
            val p = if (base.isNullOrBlank()) {
                Napier.i {
                    "use in-memory file"
                }
                MemoryFileSystemBuilder.newLinux().build().getPath("/a_file")
            } else {
                val path = Paths.get(base)
                Napier.i {
                    "use file system oss ${path.toFile().canonicalPath}"
                }
                path
            }
            FileSystemObjectStorageService(url, p)
        }

        else -> throw UnsupportedOperationException("unsupported media service type ${env["MEDIA_SERVICE"]}")
    }
}

fun buildTopicSearchService(env: MergedEnv): TopicSearchService = buildSearchService(env, {
    ElasticTopicSearchService(it)
}) { path, isInMemory ->
    LuceneTopicSearchService(path, isInMemory)
}

fun buildUserSearchService(env: MergedEnv): UserSearchService = buildSearchService(env, {
    ElasticUserSearchService(it)
}) { path, isInMemory ->
    LuceneUserSearchService(path, isInMemory)
}

fun buildRoomSearchService(env: MergedEnv): RoomSearchService = buildSearchService(env, {
    ElasticRoomSearchService(it)
}) { path, isInMemory ->
    LuceneRoomSearchService(path, isInMemory)
}

fun buildCommunitySearchService(env: MergedEnv): CommunitySearchService = buildSearchService(env, {
    ElasticCommunitySearchService(it)
}) { path, isInMemory ->
    LuceneCommunitySearchService(path, isInMemory)
}

fun<T> buildSearchService(
    env: MergedEnv,
    buildElasticService: (ElasticConnection) -> T,
    buildLuceneService: (Path, Boolean) -> T
): T {
    return when (val type = env["SEARCH_SERVICE"]) {
        "elastic" -> {
            val certFile = env["ELASTIC_CERT_FILE"] ?: throw Exception("ELASTIC_CERT_FILE is empty")
            val url = env["ELASTIC_URL"] ?: throw Exception("ELASTIC_URL is empty")
            val name = env["ELASTIC_NAME"] ?: throw Exception("ELASTIC_NAME is empty")
            val pass = env["ELASTIC_PASSWORD"] ?: throw Exception("ELASTIC_PASSWORD is empty")
            val connection = ElasticConnection(url, certFile, name, pass)
            buildElasticService(connection)
        }

        "lucene" -> {
            val luceneBase = env["LUCENE_BASE_PATH"]
            val (path, isInMemory) = if (luceneBase.isNullOrBlank()) {
                Napier.i {
                    "use in-memory document service"
                }
                MemoryFileSystemBuilder.newLinux().build().getPath("/documents") to true
            } else {
                val p = Paths.get(luceneBase)
                Napier.i {
                    "use file system lucene ${p.toFile().canonicalPath}"
                }
                p to false
            }
            buildLuceneService(path, isInMemory)
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

suspend fun Backend.processRawCommunityToCommunityInfo(
    list: List<RawCommunity>,
): Result<List<CommunityInfo>?> {
    return combinedDatabase.fileDatabase.getFileRecordByIds(list.flatMap { (community) ->
        listOf(community.iconId, community.posterId, community.fontId)
    }.filterNotNull()).mapResultIfNotNull { medias ->
        processFileRecordToFileInfo(medias).map { mediaList ->
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

suspend fun Backend.processFileRecordToFileInfo(
    fileRecords: List<FileRecord>,
): Result<List<FileInfo>> {
    return objectStorageService.get(A_FILE_DEFAULT_BUCKET, fileRecords.map {
        it.fullName
    }).map { mediaList ->
        val mediaRecordMap = mediaList.associateBy { it.fullName }
        fileRecords.map { media ->
            mediaRecordMap[media.fullName]!!.let {
                media.toFileInfo(it.url, it.lastModified)
            }
        }
    }
}

suspend fun Backend.processRawUserToUserInfo(
    rawResults: List<RawUser>,
) = combinedDatabase.fileDatabase.getFileRecordByIds(rawResults.mapNotNull {
    it.user.icon
}).mapResult { medias ->
    processFileRecordToFileInfo(medias).map { list ->
        val mediaInfoMap = list.associateBy { it.id }
        rawResults.map { pair ->
            pair.user.toUserInfo().copy(avatar = pair.user.icon?.let { mediaInfoMap[it] })
        }
    }
}

suspend fun Backend.processRawRoomToRoomInfo(list: List<RawRoom>): Result<List<RoomInfo>?> {
    return combinedDatabase.fileDatabase.getFileRecordByIds(list.mapNotNull {
        it.room.icon
    }).mapResultIfNotNull { medias ->
        processFileRecordToFileInfo(medias).map { mediaList ->
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

suspend fun <T> Backend.lockQuotaInfo(
    objectTuple: ObjectTuple,
    quotaType: QuotaType,
    length: Long,
    name: String,
    block: suspend () -> Result<T>
): Result<T> {
    try {
        val quotaInfo = getQuotaInfo(quotaType, objectTuple).getOrThrow()
        if (quotaInfo.locking) {
            throw CustomBadRequestException("quota is locking")
        }
        val id = SnowflakeFactory.nextId()
        combinedDatabase.fileDatabase.insertUploadRecord(
            UploadRecord(
                id,
                now(),
                objectTuple.objectId,
                objectTuple.objectType,
                length,
                0,
                name
            )
        ).getOrThrow()
        val t = block()
        quotaInfo.used
        combinedDatabase.fileDatabase.deleteUploadRecord(id, quotaInfo, length).getOrThrow()
        return t
    } catch (e: Exception) {
        return Result.failure(e)
    }
}

suspend fun Backend.getQuotaInfo(
    quotaType: QuotaType,
    objectTuple: ObjectTuple
) =
    combinedDatabase.containerDatabase.getQuotaInfo(objectTuple.objectId, quotaType).mapResult {
        if (it == null) {
            insertQuotaAndGet(quotaType, objectTuple)
        } else {
            Result.success(it)
        }
    }

private suspend fun Backend.insertQuotaAndGet(
    quotaType: QuotaType,
    objectTuple: ObjectTuple
): Result<QuotaInfo> {
    val (ownerId, ownerType) = objectTuple
    val quota = Quota(
        ownerId,
        ownerType,
        1024 * 1024 * 1024,
        0,
        quotaType,
        false
    )
    return combinedDatabase.containerDatabase.insertQuota(quota).mapResult {
        Result.success(quota.toQuotaInfo())
    }.recoverResult { throwable ->
        if (throwable.isDup()) {
            combinedDatabase.containerDatabase.getQuotaInfo(ownerId, quotaType)
                .mapResult {
                    if (it == null) {
                        Result.failure(Exception("get quota failed"))
                    } else {
                        Result.success(it)
                    }
                }
        } else {
            Result.failure(throwable)
        }
    }
}
