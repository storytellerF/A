package com.storyteller_f.shared.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.abs

const val AMEDIA_BUCKET = "amedia"

@Serializable
data class Dimension(val width: Int, val height: Int)

@Serializable
data class MediaInfo(val url: String, val item: MediaItem, val dimension: Dimension?)

@Serializable
data class MediaItem(
    val name: String,
    val contentType: String,
    val size: Long,
    val noPrefixName: String,
    val lastModified: LocalDateTime
)

@Serializable
class MediaResponse(val file: String, val contentType: String)

fun checkMediaDimensionRatioMatch(dimension: Dimension, aspectRatio: Dimension): Boolean {
    val aspectHeight = dimension.width.toFloat() * aspectRatio.height / aspectRatio.width
    return abs(aspectHeight - dimension.height) < 1
}
