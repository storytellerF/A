package com.storyteller_f.storage

import com.storyteller_f.shared.model.MediaInfo
import kotlinx.serialization.Serializable

enum class DownloadStatus {
    NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED
}

@Serializable
data class DownloadInfo(
    val mediaInfo: MediaInfo,
    val status: DownloadStatus,
    val message: String,
    val path: String,
)
