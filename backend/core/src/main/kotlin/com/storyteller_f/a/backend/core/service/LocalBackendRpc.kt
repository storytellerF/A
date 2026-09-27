/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.shared.type.PrimaryKey
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
    suspend fun saveTopics(documents: List<TopicDocument>)
    suspend fun getTopics(ids: List<PrimaryKey>): List<TopicDocument?>
    suspend fun cleanTopics()
    suspend fun searchTopics(search: TopicDocumentSearch): PaginationResult<TopicDocument>

    suspend fun saveUsers(documents: List<UserDocument>)
    suspend fun cleanUsers()
    suspend fun searchUsers(search: UserDocumentSearch): PaginationResult<UserDocument>

    suspend fun saveRooms(documents: List<RoomDocument>)
    suspend fun cleanRooms()
    suspend fun searchRooms(search: RoomDocumentSearch): PaginationResult<RoomDocument>

    suspend fun saveCommunities(documents: List<CommunityDocument>)
    suspend fun cleanCommunities()
    suspend fun searchCommunities(search: CommunityDocumentSearch): PaginationResult<CommunityDocument>

    suspend fun saveMembers(documents: List<MemberDocument>)
    suspend fun deleteMember(uid: PrimaryKey, objectId: PrimaryKey)
    suspend fun cleanMembers()
    suspend fun searchMembers(search: MemberDocumentSearch): PaginationResult<MemberDocument>

    suspend fun saveFiles(documents: List<FileDocument>)
    suspend fun cleanFiles()
    suspend fun searchFiles(search: FileDocumentSearch): PaginationResult<FileDocument>
}
