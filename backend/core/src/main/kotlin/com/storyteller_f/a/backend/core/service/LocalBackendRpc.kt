/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.core.service

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

@Rpc
interface LuceneRpc {
    suspend fun health(): String
    suspend fun save(index: String, documents: List<RpcLuceneDocument>)
    suspend fun get(index: String, ids: List<Long>): List<String?>
    suspend fun clean(index: String)
    suspend fun delete(index: String, query: RpcLuceneQuery)
    suspend fun search(index: String, query: RpcLuceneQuery): RpcLuceneResult
}

@Serializable
data class RpcLuceneDocument(
    val id: Long,
    val payload: String,
    val textFields: Map<String, String> = emptyMap(),
    val keywordFields: Map<String, String> = emptyMap(),
    val longFields: Map<String, Long> = emptyMap(),
)

@Serializable
data class RpcLuceneTextQuery(val word: String, val fields: List<String>)

@Serializable
data class RpcLuceneQuery(
    val mustLong: Map<String, Long> = emptyMap(),
    val mustNotLong: Map<String, Long> = emptyMap(),
    val mustLongSet: Map<String, List<Long>> = emptyMap(),
    val mustKeyword: Map<String, String> = emptyMap(),
    val text: List<RpcLuceneTextQuery> = emptyList(),
    val offset: Int = 0,
    val size: Int = 10,
    val sortByIdDescending: Boolean = false,
)

@Serializable
data class RpcLuceneResult(val payloads: List<String>, val total: Long)
