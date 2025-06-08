package com.storyteller_f.query

import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.bindPaginationQuery
import com.storyteller_f.count
import com.storyteller_f.first
import com.storyteller_f.map
import com.storyteller_f.media.UploadPack
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Media
import com.storyteller_f.tables.Medias
import com.storyteller_f.types.PrimaryKeyFetch
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import kotlin.map

fun insertMedia(data: List<Pair<PrimaryKey, UploadPack>>) {
    Medias.batchInsert(data) { (i, e) ->
        this[Medias.id] = i
        this[Medias.createdTime] = now()
        this[Medias.name] = e.name
        this[Medias.duration] = 0
        this[Medias.width] = e.dimension?.width ?: 0
        this[Medias.height] = e.dimension?.height ?: 0
        this[Medias.owner] = e.owner
        this[Medias.contentType] = e.contentType
        this[Medias.size] = e.size
        this[Medias.fullName] = e.newFullName
    }
}

suspend fun ExposedDatabaseSession.getMediaPaginationList(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
) =
    dbSearch {
        search {
            Medias.selectAll().where {
                Medias.owner eq uid
            }.bindPaginationQuery(Medias, primaryKeyFetch)
        }
        map(Media::wrapRow)
    }.mapResult { list ->
        dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.owner eq uid
                }
            }
            count()
        }.map { count ->
            list to count
        }
    }

suspend fun ExposedDatabaseSession.getMedia(owner: PrimaryKey, name: String): Result<Media?> {
    return dbSearch {
        search {
            Medias.selectAll().where {
                Medias.owner eq owner and (Medias.name eq name)
            }
        }
        first(Media::wrapRow)
    }
}

suspend fun ExposedDatabaseSession.getMediaById(id: PrimaryKey): Result<Media?> {
    return dbSearch {
        search {
            Medias.selectAll().where {
                Medias.id eq id
            }
        }
        first(Media::wrapRow)
    }
}

suspend fun ExposedDatabaseSession.getMediaListByOwner(owner: PrimaryKey): Result<List<Media>> = dbSearch {
    search {
        Medias.selectAll().where {
            Medias.owner eq owner
        }.orderBy(Medias.id, SortOrder.DESC)
    }
    map(Media::wrapRow)
}

suspend fun ExposedDatabaseSession.getMediaByNames(names: List<String?>): Result<List<Media?>> {
    if (names.filterNotNull().isEmpty()) {
        return Result.success(List(names.size) {
            null
        })
    }
    return dbSearch {
        search {
            Medias.selectAll().where {
                Medias.fullName inList names.filterNotNull()
            }.orderBy(Medias.id, SortOrder.DESC)
        }
        map(Media::wrapRow)
    }
}

fun insertCopiedMedia(
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
        it[Medias.contentType] = media.contentType
        it[Medias.size] = media.size
        it[Medias.fullName] = media.newFullName
    }.insertedCount > 0) {
        "insert media failed"
    }
}
