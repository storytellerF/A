/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.services.filesystem.api

import kotlinx.datetime.LocalDateTime
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Serializable
data class RpcUploadPack(
    val transferId: String,
    val name: String,
    val size: Long,
    val fullName: String,
    val sha256: String,
)

@Rpc
interface FilesystemRpc {
    suspend fun health(): String

    suspend fun beginUpload(bucketName: String, uploadPack: RpcUploadPack)
    suspend fun uploadChunk(transferId: String, content: ByteArray)
    suspend fun finishUpload(transferId: String): ObjectStorageWriteRecord
    suspend fun abortUpload(transferId: String)
    suspend fun get(bucketName: String, names: List<String>): List<ObjectStorageRecord>
    suspend fun cleanObjects(bucketName: String)
    suspend fun list(bucketName: String, prefix: String): List<ObjectStorageRecord>
    suspend fun copy(bucketName: String, copyPacks: List<CopyPack>): List<ObjectStorageRecord>
    suspend fun getChunk(bucketName: String, name: String, offset: Long, size: Int): ByteArray
    suspend fun compose(
        bucketName: String,
        targetFullName: String,
        sourceFullNames: List<String>,
    ): ObjectStorageWriteRecord
    suspend fun delete(bucketName: String, names: List<String>)
}

@Serializable
data class ObjectStorageRecord(val url: String, val lastModified: LocalDateTime, val fullName: String)

@Serializable
data class ObjectStorageWriteRecord(val fullName: String)

@Serializable
data class CopyPack(val originFullName: String, val newFullName: String)
