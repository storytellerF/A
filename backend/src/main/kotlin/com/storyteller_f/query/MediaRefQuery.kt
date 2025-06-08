package com.storyteller_f.query

import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.MediaRefs
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.batchInsert

suspend fun ExposedDatabaseSession.insertMediaRefs(
    objectId: PrimaryKey,
    objectType: ObjectType,
    mediaName: List<Pair<PrimaryKey, String>>
): Result<List<ResultRow>> {
    return dbQuery {
        MediaRefs.batchInsert(mediaName) {
            this[MediaRefs.objectId] = objectId
            this[MediaRefs.objectType] = objectType
            this[MediaRefs.mediaName] = it.second
            this[MediaRefs.author] = it.first
        }
    }
}
