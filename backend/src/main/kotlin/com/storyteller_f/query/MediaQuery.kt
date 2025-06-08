package com.storyteller_f.query

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.bindPaginationQuery
import com.storyteller_f.count
import com.storyteller_f.first
import com.storyteller_f.map
import com.storyteller_f.media.CopyPack
import com.storyteller_f.media.UploadPack
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Media
import com.storyteller_f.tables.Media.Companion.wrapRow
import com.storyteller_f.tables.Medias
import com.storyteller_f.tables.toMediaInfo
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PrimaryKeyFetch
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import kotlin.map

suspend fun Backend.uploadFiles(uploadPacks: List<UploadPack>): Result<List<MediaInfo?>> {
    val data = uploadPacks.mapIndexed { i, e ->
        SnowflakeFactory.nextId() to e
    }
    return exposedDatabaseSession.dbQuery {
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
        mediaService.upload(AMEDIA_DEFAULT_BUCKET, uploadPacks).getOrThrow()
    }.map { urls ->
        urls.mapIndexed { i, e ->
            if (e != null) {
                MediaInfo(
                    data[i].first,
                    e.first,
                    uploadPacks[i].newFullName,
                    uploadPacks[i].contentType,
                    uploadPacks[i].size,
                    uploadPacks[i].name,
                    uploadPacks[i].owner,
                    e.second,
                    uploadPacks[i].dimension
                )
            } else {
                null
            }
        }
    }
}

suspend fun Backend.getMediaPaginationResult(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<MediaInfo>> {
    return exposedDatabaseSession.dbSearch {
        search {
            Medias.selectAll().where {
                Medias.owner eq uid
            }.bindPaginationQuery(Medias, primaryKeyFetch)
        }
        map(Media::wrapRow)
    }.mapResult { list ->
        exposedDatabaseSession.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.owner eq uid
                }
            }
            count()
        }.mapResult { count ->
            val names = list.map {
                "${it.owner}/${it.name}"
            }
            mediaService.get(AMEDIA_DEFAULT_BUCKET, names).map { mediaUrls ->
                val data = mediaUrls.mapIndexedNotNull { i, e ->
                    if (e != null) {
                        val dimension = if (list[i].contentType.startsWith("image")) {
                            Dimension(list[i].width, list[i].height)
                        } else {
                            null
                        }
                        MediaInfo(
                            list[i].id,
                            e.first,
                            names[i], list[i].contentType, list[i].size, list[i].name, list[i].owner, e.second,
                            dimension
                        )
                    } else {
                        null
                    }
                }
                PaginationResult(data, count)
            }
        }
    }
}

suspend fun Backend.getMedia(owner: PrimaryKey, name: String): Result<Media?> {
    return exposedDatabaseSession.dbSearch {
        search {
            Medias.selectAll().where {
                Medias.owner eq owner and (Medias.name eq name)
            }
        }
        first(Media::wrapRow)
    }
}

suspend fun Backend.getMediaById(id: PrimaryKey): Result<Media?> {
    return exposedDatabaseSession.dbSearch {
        search {
            Medias.selectAll().where {
                Medias.id eq id
            }
        }
        first(Media::wrapRow)
    }
}

suspend fun Backend.getMediaInfoList(
    owner: PrimaryKey,
): Result<List<MediaInfo?>?> {
    return exposedDatabaseSession.dbSearch {
        search {
            Medias.selectAll().where {
                Medias.owner eq owner
            }.orderBy(Medias.id, SortOrder.DESC)
        }
        map(Media::wrapRow)
    }.mapResultIfNotNull { medias ->
        mediaService.get(AMEDIA_DEFAULT_BUCKET, medias.map {
            it.newFullName
        }).mapIfNotNull {
            medias.mapIndexed { i, e ->
                it[i]?.let { it1 -> e.toMediaInfo(it1) }
            }
        }
    }
}

suspend fun Backend.getMediaInfoList(names: List<String?>): Result<List<MediaInfo?>?> {
    if (names.filterNotNull().isEmpty()) {
        return Result.success(List(names.size) {
            null
        })
    }
    return exposedDatabaseSession.dbSearch {
        search {
            Medias.selectAll().where {
                Medias.fullName inList names.filterNotNull()
            }.orderBy(Medias.id, SortOrder.DESC)
        }
        map(Media::wrapRow)
    }.mapResult { medias ->
        val mediaMap = medias.associateBy { it.newFullName }
        mediaService.get(AMEDIA_DEFAULT_BUCKET, names.map {
            mediaMap[it]?.newFullName
        }).map {
            names.mapIndexed { i, e ->
                if (e != null) {
                    it[i]?.let { it1 -> mediaMap[e]?.toMediaInfo(it1) }
                } else {
                    null
                }
            }
        }
    }
}

suspend fun Backend.copyMedia(
    media: Media,
    newOwner: PrimaryKey,
    newName: String
): Result<ServerResponse<MediaInfo?>> {
    val id = SnowflakeFactory.nextId()
    return exposedDatabaseSession.dbQuery {
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
        mediaService.copy(
            AMEDIA_DEFAULT_BUCKET,
            listOf(CopyPack("${media.owner}/${media.name}", newName))
        ).map { list ->
            ServerResponse(list.map {
                if (it != null) {
                    val dimension =
                        if (media.width != 0 && media.height != 0) Dimension(media.width, media.height) else null
                    MediaInfo(
                        id,
                        it.first,
                        newName,
                        media.contentType,
                        media.size,
                        media.name,
                        newOwner,
                        it.second,
                        dimension
                    )
                } else {
                    null
                }
            }, null)
        }.getOrThrow()
    }
}
