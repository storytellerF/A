package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.abs

const val AMEDIA_DEFAULT_BUCKET = "default"

@Serializable
data class Dimension(val width: Int, val height: Int)

@Serializable
data class MediaInfo(
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
        get() = ObjectType.MEDIA
}

fun checkMediaDimensionRatioMatch(dimension: Dimension, aspectRatio: Dimension): Boolean {
    val aspectHeight = dimension.width.toFloat() * aspectRatio.height / aspectRatio.width
    val abs = abs(aspectHeight - dimension.height)
    Napier.i {
        "checkMediaDimensionRatioMatch $dimension $aspectRatio $abs $aspectHeight"
    }
    return abs < 1
}
