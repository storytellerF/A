package com.storyteller_f.storage

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

enum class UploadStatus {
    NOT_UPLOADING,
    UPLOADING, PAUSED, FAILED,
    SUCCESS
}

@Serializable
data class UploadInfo(
    val id: PrimaryKey,
    val objectId: PrimaryKey,
    val pathHash: String,
    val path: String,
    val sha256: String,
    val progress: Long,
    val chunkProgress: Long,
    val total: Long,
    val status: UploadStatus,
    val message: String,
    val name: String,
    val contentType: String,
    val chunkSize: Long,
    // 对应的上传记录 id
    val recordId: PrimaryKey? = null
) {
    companion object {
        val EMPTY = UploadInfo(0, 0, "", "", "", 0, 0, 0, UploadStatus.NOT_UPLOADING, "", "", "", 0,)
    }
}
