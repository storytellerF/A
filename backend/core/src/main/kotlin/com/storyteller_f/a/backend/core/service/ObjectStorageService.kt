package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.shared.model.Dimension
import kotlinx.datetime.LocalDateTime
import java.io.File
import java.io.InputStream

data class ObjectStorageRecord(val url: String, val lastModified: LocalDateTime, val fullName: String)

data class UploadPack(
    val file: File,
    val name: String,
    val size: Long,
    val fullName: String
)

data class ProcessedUploadPack(
    val pack: UploadPack,
    val contentType: String,
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

    /**
     * 使用对象存储的 compose/concat 能力，将同一 bucket 下的多个源对象顺序合并为一个目标对象。
     * @param bucketName 目标与源对象所在的 bucket（需相同）
     * @param targetFullName 合并后目标对象完整路径
     * @param sourceFullNames 源对象完整路径列表，按顺序依次合并
     */
    suspend fun compose(
        bucketName: String,
        targetFullName: String,
        sourceFullNames: List<String>
    ): Result<ObjectStorageRecord>

    /**
     * 删除 bucket 下的多个对象。
     */
    suspend fun delete(bucketName: String, names: List<String>): Result<Unit>
}

interface ObjectStorageServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): ObjectStorageService
}
