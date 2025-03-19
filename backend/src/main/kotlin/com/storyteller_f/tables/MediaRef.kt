package com.storyteller_f.tables

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.MEDIA_NAME_LENGTH
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.objectType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert

object MediaRefs : Table() {
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
    val author = customPrimaryKey("owner")
    val mediaName = varchar("media_name", MEDIA_NAME_LENGTH)
}

class MediaRef(val objectId: PrimaryKey, val objectType: ObjectType, val author: PrimaryKey, val mediaName: String) {
    companion object {
        fun wrapRow(resultRow: ResultRow): MediaRef {
            return MediaRef(
                resultRow[MediaRefs.objectId],
                resultRow[MediaRefs.objectType],
                resultRow[MediaRefs.author],
                resultRow[MediaRefs.mediaName]
            )
        }
    }
}

suspend fun DatabaseFactory.insertMediaRefs(
    objectId1: PrimaryKey,
    objectType1: ObjectType,
    mediaName: List<Pair<PrimaryKey, String>>
): Result<List<ResultRow>> {
    return dbQuery {
        MediaRefs.batchInsert(mediaName) {
            this[MediaRefs.objectId] = objectId1
            this[MediaRefs.objectType] = objectType1
            this[MediaRefs.mediaName] = it.second
            this[MediaRefs.author] = it.first
        }
    }
}
