package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.BaseEntity
import com.storyteller_f.backend.service.BaseTable
import com.storyteller_f.backend.service.customPrimaryKey
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Medias : BaseTable() {
    val name = varchar("name", 200)
    val fullName = varchar("full_name", 200).index()
    val duration = long("duration")
    val width = integer("width")
    val height = integer("height")
    val owner = customPrimaryKey("owner")
    val contentType = varchar("content_type", 50)
    val size = long("size")

    init {
        index("medias-main", true, owner, name)
        index("medias-size", false, owner, size)
        index("medias-type", false, owner, contentType, size)
        index("medias-full-name", false, fullName)
    }
}

class Media(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val name: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val owner: PrimaryKey,
    val contentType: String,
    val size: Long,
) : BaseEntity(id, createdTime) {
    val newFullName: String = "$owner/$name"
    companion object {
        fun wrapRow(resultRow: ResultRow): Media {
            return with(Medias) {
                Media(
                    resultRow[id],
                    resultRow[createdTime],
                    resultRow[name],
                    resultRow[duration],
                    resultRow[width],
                    resultRow[height],
                    resultRow[owner],
                    resultRow[contentType],
                    resultRow[size],
                )
            }
        }
    }
}

fun Media.toMediaInfo(it: Pair<String, LocalDateTime>): MediaInfo {
    return MediaInfo(
        id,
        it.first,
        newFullName,
        contentType,
        size,
        name,
        owner,
        it.second,
        if (width != 0 && height != 0) Dimension(width, height) else null
    )
}
