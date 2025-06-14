package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.*
import com.storyteller_f.backend.service.media.UploadPack
import com.storyteller_f.backend.service.tables.Media
import com.storyteller_f.backend.service.tables.Medias
import com.storyteller_f.backend.service.types.PrimaryKeyFetch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.sql.*

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
        map(Media.Companion::wrapRow)
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

suspend fun ExposedDatabaseSession.getMediaByIds(ids: List<PrimaryKey>): Result<List<Media>> {
    return dbSearch {
        search {
            Medias.selectAll().where {
                Medias.id inList ids
            }
        }
        map(Media::wrapRow)
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

suspend fun ExposedDatabaseSession.getMediaByNames(names: List<String?>): Result<List<Media>> {
    if (names.filterNotNull().isEmpty()) {
        return Result.success(emptyList())
    }
    return dbSearch {
        search {
            Medias.selectAll().where {
                Medias.fullName inList names.filterNotNull()
            }.orderBy(Medias.id, SortOrder.DESC)
        }
        map(Media.Companion::wrapRow)
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
        it[Medias.fullName] = media.fullName
    }.insertedCount > 0) {
        "insert media failed"
    }
}
