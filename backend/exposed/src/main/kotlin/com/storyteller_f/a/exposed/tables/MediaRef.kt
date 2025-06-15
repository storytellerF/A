package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.MEDIA_NAME_LENGTH
import com.storyteller_f.backend.service.customPrimaryKey
import com.storyteller_f.backend.service.objectType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

object MediaRefs : Table() {
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
    val author = customPrimaryKey("owner")
    val mediaName = varchar("media_name", MEDIA_NAME_LENGTH)
}

class MediaRef(val objectId: PrimaryKey, val objectType: ObjectType, val author: PrimaryKey, val mediaName: String) {
    companion object {
        fun wrapRow(resultRow: ResultRow): MediaRef {
            return with(MediaRefs) {
                MediaRef(
                    resultRow[objectId],
                    resultRow[objectType],
                    resultRow[author],
                    resultRow[mediaName]
                )
            }
        }
    }
}
