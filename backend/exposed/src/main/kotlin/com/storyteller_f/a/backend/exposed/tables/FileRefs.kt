package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.MEDIA_NAME_LENGTH
import com.storyteller_f.a.backend.core.types.FileRef
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

object FileRefs : Table() {
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
    val author = customPrimaryKey("owner")
    val mediaName = varchar("media_name", MEDIA_NAME_LENGTH)
}

fun FileRef.Companion.wrapRow(resultRow: ResultRow): FileRef {
    return with(FileRefs) {
        FileRef(
            resultRow[objectId],
            resultRow[objectType],
            resultRow[author],
            resultRow[mediaName]
        )
    }
}
