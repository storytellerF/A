package com.storyteller_f.backend.service.media

import com.storyteller_f.a.backend.core.CopyPack
import com.storyteller_f.a.backend.core.MinIoConnection
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.shared.utils.mapResult
import io.github.aakira.napier.Napier
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.http.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.Result
import kotlin.getOrThrow

class MinIoMediaService(private val connection: MinIoConnection) : MediaService {
    override suspend fun clean(bucketName: String): Result<Unit> {
        return useMinIoClient(connection) {
            if (bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                removeAllObject(bucketName)
                Napier.i {
                    "clean media done"
                }
            } else {
                Napier.i {
                    "bucket not exists"
                }
            }
        }
    }

    override suspend fun list(bucketName: String, prefix: String): Result<List<MediaRecord>> {
        return useMinIoClient(connection) {
            val names = listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).recursive(false).build()
            ).map {
                it.get().objectName()
            }
            get(bucketName, names).getOrThrow()
        }
    }

    override suspend fun copy(
        bucketName: String,
        copyPacks: List<CopyPack>
    ): Result<List<MediaRecord>> {
        return useMinIoClient(connection) {
            copyPacks.map {
                copyObject(
                    CopyObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(it.new)
                        .metadataDirective(Directive.COPY)
                        .taggingDirective(Directive.COPY)
                        .source(
                            CopySource.builder()
                                .bucket(bucketName)
                                .`object`(it.origin)
                                .build()
                        )
                        .build()
                ).`object`()
            }
        }.mapResult {
            get(bucketName, it)
        }
    }

    override suspend fun getInputStream(
        bucketName: String,
        name: String
    ): Result<InputStream> {
        return useMinIoClient(connection) {
            getObject(GetObjectArgs.builder().bucket(bucketName).`object`(name).build())
        }
    }

    override suspend fun get(bucketName: String, names: List<String>): Result<List<MediaRecord>> {
        return useMinIoClient(connection) {
            names.mapNotNull {
                try {
                    val statObject =
                        statObject(StatObjectArgs.builder().bucket(bucketName).`object`(it).build())
                    val url = getMinioObjectUrl(bucketName, it)
                    if (url != null) {
                        MediaRecord(url, statObject.lastModified().toLocalDateTime().toKotlinLocalDateTime(), it)
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

    override suspend fun upload(
        bucketName: String,
        uploadPacks: List<UploadPack>
    ): Result<List<MediaRecord>> {
        return useMinIoClient(connection) {
            if (!bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
            }
            val names = uploadPacks.map {
                uploadObject(
                    UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(it.newFullName)
                        .filename(it.path.absolutePath)
                        .build()
                ).`object`()
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
                    point.initCause(e)
                    Napier.e(throwable = point) {
                        "minio error"
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
