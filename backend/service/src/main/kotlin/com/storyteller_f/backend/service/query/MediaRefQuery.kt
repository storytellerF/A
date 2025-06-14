package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.tables.MediaRefs
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
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
