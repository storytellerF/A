package com.storyteller_f.media

import com.storyteller_f.MinIoConnection
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.MediaItem
import io.github.aakira.napier.Napier
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.http.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.Exception
import kotlin.Pair
import kotlin.Result
import kotlin.String
import kotlin.Unit
import kotlin.apply
import kotlin.collections.List
import kotlin.collections.filterNotNull
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.getOrThrow
import kotlin.map
import kotlin.runCatching
import kotlin.text.startsWith
import kotlin.text.substringAfter
import kotlin.text.toIntOrNull
import kotlin.to
import kotlin.use

class MinIoMediaService(private val connection: MinIoConnection) : MediaService {
    override suspend fun clean(bucketName: String): Result<Unit> {
        return useMinIoClient(connection) {
            if (bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                removeAllObject(bucketName)
            }
        }
    }

    override suspend fun list(bucketName: String, prefix: String): Result<List<MediaInfo>> {
        return useMinIoClient(connection) {
            val names = listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).recursive(false).build()
            ).map {
                it.get().objectName()
            }
            get(bucketName, names).map {
                it.filterNotNull()
            }.getOrThrow()
        }
    }

    private fun MinioClient.stat(
        bucketName: String,
        objName: String
    ): Pair<MediaItem, Dimension?> {
        val statObject =
            statObject(StatObjectArgs.builder().bucket(bucketName).`object`(objName).build())
        val dimension = if (statObject.contentType().startsWith("image")) {
            val metadata = statObject.userMetadata()
            val width = metadata["width"]?.toIntOrNull()
            val height = metadata["height"]?.toIntOrNull()
            if (width != null && height != null) {
                Dimension(width, height)
            } else {
                null
            }
        } else {
            null
        }
        return MediaItem(
            objName,
            statObject.contentType(),
            statObject.size(),
            objName.substringAfter("/"),
            statObject.lastModified().toLocalDateTime().toKotlinLocalDateTime()
        ) to dimension
    }

    override suspend fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>> {
        return useMinIoClient(connection) {
            objList.map {
                if (it == null) {
                    null
                } else {
                    try {
                        val url = getMinioObjectUrl(bucketName, it)
                        if (url != null) {
                            val (item, dimension) = stat(bucketName, it)
                            MediaInfo(url, item, dimension)
                        } else {
                            null
                        }
                    } catch (e: ErrorResponseException) {
                        if (e.errorResponse().code() == "NoSuchKey") {
                            null
                        } else {
                            throw e
                        }
                    }
                }
            }
        }
    }

    override suspend fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>> {
        return useMinIoClient(connection) {
            if (!bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
            }
            val names = list.map { (objName, picFullPath, type, meta) ->
                val response = uploadObject(
                    UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(objName)
                        .filename(picFullPath.absolutePath)
                        .userMetadata(meta)
                        .apply {
                            if (type != null) {
                                contentType(type)
                            }
                        }
                        .build()
                )
                response.`object`()
            }
            get(bucketName, names).getOrThrow()
        }
    }
}

private suspend fun <R> useMinIoClient(
    minIoConnection: MinIoConnection,
    block: suspend MinioClient.() -> R
): Result<R> {
    val point = Exception()
    return runCatching {
        MinioClient.builder()
            .endpoint(minIoConnection.url)
            .credentials(minIoConnection.user, minIoConnection.pass)
            .build().use {
                try {
                    withContext(Dispatchers.IO) {
                        it.block()
                    }
                } catch (e: Exception) {
                    Napier.e(throwable = point) {
                        "minio error $e"
                    }
                    throw e
                }
            }
    }
}

private fun MinioClient.removeAllObject(bucketName: String) {
    listObjects(ListObjectsArgs.builder().bucket(bucketName).recursive(true).build()).forEach {
        if (!it.get().isDir) {
            removeObject(RemoveObjectArgs.builder().bucket(bucketName).`object`(it.get().objectName()).build())
        }
    }
}

private fun MinioClient.getMinioObjectUrl(bucketName: String, objName: String?): String? {
    objName ?: return null
    return getPresignedObjectUrl(
        GetPresignedObjectUrlArgs.builder()
            .method(Method.GET)
            .bucket(bucketName)
            .`object`(objName)
            .expiry(7, TimeUnit.DAYS)
            .build()
    )
}
