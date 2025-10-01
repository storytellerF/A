package com.storyteller_f.a.backend.minio

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.MinIoConnection
import com.storyteller_f.a.backend.core.service.CacheServiceFactory
import com.storyteller_f.a.backend.core.service.CopyPack
import com.storyteller_f.a.backend.core.service.ObjectStorageRecord
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.ObjectStorageServiceFactory
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.recoverResult
import io.github.aakira.napier.Napier
import io.mikael.urlbuilder.UrlBuilder
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.http.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.InputStream
import java.net.URI
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit
import kotlin.Result
import kotlin.String
import kotlin.getOrThrow
import kotlin.time.ExperimentalTime

class MinIoObjectStorageService(
    private val connection: MinIoConnection,
    private val minioHost: String?
) : ObjectStorageService {
    val cache =
        ServiceLoader.load(CacheServiceFactory::class.java).first {
            it.match(MergedEnv(emptyList()))
        }.build<String, String>(
            MergedEnv(emptyList()),
            String::class
        )

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

    override suspend fun list(
        bucketName: String,
        prefix: String
    ): Result<List<ObjectStorageRecord>> {
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
        copyPacks: List<CopyPack>,
    ): Result<List<ObjectStorageRecord>> {
        return useMinIoClient(connection) {
            copyPacks.map {
                copyObject(
                    CopyObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(it.newFullName)
                        .metadataDirective(Directive.COPY)
                        .taggingDirective(Directive.COPY)
                        .source(
                            CopySource.builder()
                                .bucket(bucketName)
                                .`object`(it.originFullName)
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
        name: String,
    ): Result<InputStream> {
        return useMinIoClient(connection) {
            getObject(GetObjectArgs.builder().bucket(bucketName).`object`(name).build())
        }
    }

    override suspend operator fun get(
        bucketName: String,
        names: List<String>
    ): Result<List<ObjectStorageRecord>> {
        return useMinIoClient(connection) {
            names.mapNotNull {
                try {
                    val url = cache.get(it) {
                        val minioObjectUrl = getMinioObjectUrl(bucketName, it)
                        if (minioHost.isNullOrBlank()) {
                            minioObjectUrl
                        } else {
                            replaceUrl(minioHost, minioObjectUrl)
                        }
                    }
                    val statObject =
                        statObject(StatObjectArgs.builder().bucket(bucketName).`object`(it).build())
                    val lastModified =
                        statObject.lastModified().toLocalDateTime().toKotlinLocalDateTime()
                    ObjectStorageRecord(url, lastModified, it)
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
        uploadPacks: List<UploadPack>,
    ): Result<List<ObjectStorageRecord>> {
        return useMinIoClient(connection) {
            if (!bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
            }
            val names = uploadPacks.map {
                uploadObject(
                    UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(it.fullName)
                        .filename(it.path.absolutePath)
                        .build()
                ).`object`()
            }
            get(bucketName, names).getOrThrow()
        }
    }
}

fun replaceUrl(minioHost: String, minioObjectUrl: String?): String {
    val host = URI.create(minioHost)
    return UrlBuilder.fromString(minioObjectUrl)
        .withHost(host.host)
        .withPort(host.port)
        .withScheme(host.scheme)
        .toString()
}

private suspend fun <R> useMinIoClient(
    minIoConnection: MinIoConnection,
    block: suspend MinioClient.() -> R,
): Result<R> {
    val point = Exception()
    return runCatching {
        MinioClient.builder()
            .endpoint(minIoConnection.url)
            .credentials(minIoConnection.user, minIoConnection.pass)
            .build().use {
                withContext(Dispatchers.IO) {
                    it.block()
                }
            }
    }.recoverResult { e ->
        point.initCause(e)
        Napier.e(throwable = point) {
            "minio error"
        }
        Result.failure(point)
    }
}

private fun MinioClient.removeAllObject(bucketName: String) {
    listObjects(ListObjectsArgs.builder().bucket(bucketName).recursive(true).build()).forEach {
        if (!it.get().isDir) {
            removeObject(
                RemoveObjectArgs.builder().bucket(bucketName).`object`(it.get().objectName())
                    .build()
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun MinioClient.getMinioObjectUrl(bucketName: String, objName: String) =
    getPresignedObjectUrl(
        GetPresignedObjectUrlArgs.builder()
            .method(Method.GET)
            .bucket(bucketName)
            .`object`(objName)
            .expiry(7, TimeUnit.DAYS)
            .build()
    )

class MinioObjectStorageServiceFactory : ObjectStorageServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["MEDIA_SERVICE"] == "minio"
    }

    override fun build(env: MergedEnv): ObjectStorageService {
        val url = env["MINIO_URL"] ?: throw Exception("MINIO_URL is empty")
        val name = env["MINIO_NAME"] ?: throw Exception("MINIO_NAME is empty")
        val pass = env["MINIO_PASS"] ?: throw Exception("MINIO_PASS is empty")
        return MinIoObjectStorageService(MinIoConnection(url, name, pass), env["MINIO_HOST"])
    }
}
