package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.abs

const val A_FILE_DEFAULT_BUCKET = "default"

@Serializable
data class Dimension(val width: Int, val height: Int)

@Serializable
data class FileInfo(
    override val id: PrimaryKey,
    val url: String,
    val fullName: String,
    val contentType: String,
    val size: Long,
    val name: String,
    val owner: PrimaryKey,
    val ownerType: ObjectType,
    val lastModified: LocalDateTime,
    val dimension: Dimension?,
) : PrimaryKeyIdentifiable {
    override val objectType: ObjectType
        get() = ObjectType.File

    companion object {
        const val PDF_CONTENT_TYPE = "application/pdf"
        const val M3U8_MIMETYPE = "application/vnd.apple.mpegurl"
        const val YOUTUBE_MIMETYPE = "video/youtube"
        const val SOUND_CLOUD_MIME_TYPE = "audio/sound.cloud"
    }
}

fun checkMediaFileDimensionRatioMatch(dimension: Dimension, aspectRatio: Dimension): Boolean {
    val aspectHeight = dimension.width.toFloat() * aspectRatio.height / aspectRatio.width
    val abs = abs(aspectHeight - dimension.height)
    Napier.i {
        "checkMediaDimensionRatioMatch $dimension $aspectRatio $abs $aspectHeight"
    }
    return abs < 1
}
