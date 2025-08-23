package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.batchInsert

object FileRecords : BaseTable() {
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
        index("files-main", true, owner, name)
        index("files-size", false, owner, size)
        index("files-type", false, owner, contentType, size)
        index("files-full-name", false, fullName)
    }
}

fun FileRecord.Companion.wrapRow(resultRow: ResultRow): FileRecord {
    return with(FileRecords) {
        FileRecord(
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
            resultRow[fullName]
        )
    }
}
