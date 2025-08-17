package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Media
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert

object Medias : BaseTable() {
    val name = varchar("name", 200)
    val fullName = varchar("full_name", 200).index()
    val duration = long("duration")
    val width = integer("width")
    val height = integer("height")
    val owner = customPrimaryKey("owner")
    val ownerType = enumerationByName<ObjectType>("owner_type", 10)
    val contentType = varchar("content_type", 50)
    val size = long("size")

    init {
        index("medias-main", true, owner, name)
        index("medias-size", false, owner, size)
        index("medias-type", false, owner, contentType, size)
        index("medias-full-name", false, fullName)
    }
}

fun Media.Companion.wrapRow(resultRow: ResultRow): Media {
    return with(Medias) {
        Media(
            resultRow[id],
            resultRow[createdTime],
            resultRow[name],
            resultRow[duration],
            resultRow[width],
            resultRow[height],
            resultRow[owner],
            resultRow[ownerType],
            resultRow[contentType],
            resultRow[size],
        )
    }
}

suspend fun Media.Companion.insertMediaList(
    mediaList: List<Media>
) {
    Medias.batchInsert(mediaList) { e ->
        this[Medias.id] = e.id
        this[Medias.createdTime] = now()
        this[Medias.name] = e.name
        this[Medias.duration] = 0
        this[Medias.width] = e.width
        this[Medias.height] = e.height
        this[Medias.owner] = e.owner
        this[Medias.ownerType] = e.ownerType
        this[Medias.contentType] = e.contentType
        this[Medias.size] = e.size
        this[Medias.fullName] = e.fullName
    }
}

suspend fun Media.Companion.insertCopiedMedia(
    id: PrimaryKey,
    media: Media,
    newOwner: PrimaryKey
) {
    check(Medias.insert {
        it[Medias.id] = id
        it[Medias.createdTime] = now()
        it[Medias.name] = media.name
        it[Medias.duration] = media.duration
        it[Medias.width] = media.width
        it[Medias.height] = media.height
        it[Medias.owner] = newOwner
        it[Medias.ownerType] = media.ownerType
        it[Medias.contentType] = media.contentType
        it[Medias.size] = media.size
        it[Medias.fullName] = media.fullName
    }.insertedCount > 0) {
        "insert media failed"
    }
}
fun Media.toMediaInfo(url: String, lastModified: LocalDateTime): FileInfo {
    return FileInfo(
        id,
        url,
        fullName,
        contentType,
        size,
        name,
        owner,
        ownerType,
        lastModified,
        if (width != 0 && height != 0) Dimension(width, height) else null
    )
}
