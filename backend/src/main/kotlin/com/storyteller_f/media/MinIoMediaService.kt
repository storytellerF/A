package com.storyteller_f.media

import com.storyteller_f.MinIoConnection
import io.minio.*
import io.minio.http.Method
import java.util.concurrent.TimeUnit

class MinIoMediaService(private val connection: MinIoConnection) : MediaService {
    override fun clean(bucketName: String) {
        useMinIoClient(connection) {
            if (bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                removeAllObject(bucketName)
            }
        }
    }

    override fun get(bucketName: String, objList: List<String?>): List<String?> {
        return useMinIoClient(connection) {
            objList.map {
                getIconInMioIo(bucketName, it)
            }
        }
    }

    override fun upload(bucketName: String, list: List<Pair<String, String>>) {
        useMinIoClient(connection) {
            if (!bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
            }
            list.forEach { (objName, picFullPath) ->
                uploadObject(
                    UploadObjectArgs.builder().bucket(bucketName).`object`(objName).filename(picFullPath).build()
                )
            }
        }
    }
}

private fun <R> useMinIoClient(minIoConnection: MinIoConnection, block: MinioClient.() -> R): R {
    return MinioClient.builder()
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
