package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

data class FileRecord(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val name: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val owner: PrimaryKey,
    val ownerType: ObjectType,
    val contentType: String,
    val size: Long,
    val fullName: String
) {
    val dimension = Dimension(width, height)

    companion object
}

fun FileRecord.toFileInfo(url: String, lastModified: LocalDateTime): FileInfo {
    return FileInfo(id, url, fullName, contentType, size, name, owner, ownerType, lastModified, dimension)
}
