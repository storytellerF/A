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
    val pathHash: String,
    val path: String,
    val progress: Long,
    val total: Long?,
    val status: UploadStatus,
    val message: String,
)
