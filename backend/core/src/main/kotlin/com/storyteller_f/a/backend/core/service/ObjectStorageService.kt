package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.shared.model.Dimension
import kotlinx.datetime.LocalDateTime
import java.io.File
import java.io.InputStream

data class ObjectStorageRecord(val url: String, val lastModified: LocalDateTime, val fullName: String)

data class UploadPack(
    val path: File,
    val name: String,
    val size: Long,
    val fullName: String
)

data class ProcessedUploadPack(
    val pack: UploadPack,
    val contentType: String = "",
    val dimension: Dimension? = null
)

data class CopyPack(val originFullName: String, val newFullName: String)

interface ObjectStorageService {
    suspend fun upload(bucketName: String, uploadPacks: List<UploadPack>): Result<List<ObjectStorageRecord>>

    /**
     * @param names 完整的name
     */
    suspend fun get(bucketName: String, names: List<String>): Result<List<ObjectStorageRecord>>

    suspend fun clean(bucketName: String): Result<Unit>

    suspend fun list(bucketName: String, prefix: String): Result<List<ObjectStorageRecord>>

    suspend fun copy(bucketName: String, copyPacks: List<CopyPack>): Result<List<ObjectStorageRecord>>

    suspend fun getInputStream(bucketName: String, name: String): Result<InputStream>
}

interface ObjectStorageServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): ObjectStorageService
}
