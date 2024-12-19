package com.storyteller_f.media

import com.storyteller_f.MinIoConnection
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.MediaItem
import io.minio.*
import io.minio.http.Method
import java.util.concurrent.TimeUnit
import kotlin.Result

class MinIoMediaService(private val connection: MinIoConnection) : MediaService {
    override fun clean(bucketName: String): Result<Unit> {
        return useMinIoClient(connection) {
            if (bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                removeAllObject(bucketName)
            }
        }
    }

    override fun list(bucketName: String, prefix: String): Result<List<MediaInfo?>> {
        return useMinIoClient(connection) {
            val names = listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).recursive(false).build()
            ).map {
                it.get().objectName()
            }
            get(bucketName, names).getOrThrow()
        }
    }

    private fun MinioClient.stat(
        bucketName: String,
        objName: String
    ): MediaItem {
        val statObject =
            statObject(StatObjectArgs.builder().bucket(bucketName).`object`(objName).build())
        return MediaItem(objName, statObject.contentType(), statObject.size())
    }

    override fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>> {
        return useMinIoClient(connection) {
            objList.map {
                if (it == null) {
                    null
                } else {
                    val url = getIconInMioIo(bucketName, it)
                    if (url != null) {
                        val item = stat(bucketName, it)
                        MediaInfo(url, item)
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>> {
        return useMinIoClient(connection) {
            if (!bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
            }
            val names = list.map { (objName, picFullPath) ->
                val response = uploadObject(
                    UploadObjectArgs.builder().bucket(
                        bucketName
                    ).`object`(objName).filename(picFullPath.absolutePath).build()
                )
                response.`object`()
            }
            get(bucketName, names).getOrThrow()
        }
    }
}

private fun <R> useMinIoClient(minIoConnection: MinIoConnection, block: MinioClient.() -> R): Result<R> {
    return runCatching {
        MinioClient.builder()
            .endpoint(minIoConnection.url)
            .credentials(minIoConnection.user, minIoConnection.pass)
            .build().use {
                try {
                    it.block()
                } catch (e: Exception) {
                    e.printStackTrace()
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

private fun MinioClient.getIconInMioIo(bucketName: String, objName: String?): String? {
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
