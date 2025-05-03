package com.storyteller_f.tables

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
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
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

object Medias : BaseTable() {
    val name = varchar("name", 200)
    val fullName = varchar("full_name", 200).index()
    val duration = long("duration")
    val width = integer("width")
    val height = integer("height")
    val owner = customPrimaryKey("owner")
    val contentType = varchar("content_type", 50)
    val size = long("size")

    init {
        index("medias-main", true, owner, name)
        index("medias-size", false, owner, size)
        index("medias-type", false, owner, contentType, size)
    }
}

class Media(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val name: String,
    val fullName: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val owner: PrimaryKey,
    val contentType: String,
    val size: Long,
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): Media {
            return with(Medias) {
                Media(
                    resultRow[id],
                    resultRow[createdTime],
                    resultRow[name],
                    resultRow[fullName],
                    resultRow[duration],
                    resultRow[width],
                    resultRow[height],
                    resultRow[owner],
                    resultRow[contentType],
                    resultRow[size],
                )
            }
        }
    }
}

fun Media.toMediaInfo(it: Pair<String, LocalDateTime>): MediaInfo {
    return MediaInfo(
        id,
        it.first,
        fullName,
        contentType,
        size,
        name,
        owner,
        it.second,
        if (width != 0 && height != 0) Dimension(width, height) else null
    )
}

suspend fun DatabaseFactory.uploadFiles(backend: Backend, uploadPacks: List<UploadPack>): Result<List<MediaInfo?>> {
    val data = uploadPacks.mapIndexed { i, e ->
        SnowflakeFactory.nextId() to e
    }
    return dbQuery(backend) {
        Medias.batchInsert(data) { (i, e) ->
            this[Medias.id] = i
            this[Medias.createdTime] = now()
            this[Medias.name] = e.noPrefixName
            this[Medias.duration] = 0
            this[Medias.width] = e.dimension?.width ?: 0
            this[Medias.height] = e.dimension?.height ?: 0
            this[Medias.owner] = e.owner
            this[Medias.contentType] = e.contentType
            this[Medias.size] = e.size
            this[Medias.fullName] = e.name
        }
        backend.mediaService.upload(AMEDIA_DEFAULT_BUCKET, uploadPacks).getOrThrow()
    }.map { urls ->
        urls.mapIndexed { i, e ->
            if (e != null) {
                MediaInfo(
                    data[i].first,
                    e.first,
                    uploadPacks[i].name,
                    uploadPacks[i].contentType,
                    uploadPacks[i].size,
                    uploadPacks[i].noPrefixName,
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

suspend fun DatabaseFactory.getPagingMedias(
    backend: Backend,
    uid: PrimaryKey,
    pagingFetch: PagingFetch
): Result<PaginationResult<MediaInfo>> {
    return mapQuery(backend, Media::wrapRow) {
        Medias.selectAll().where {
            Medias.owner eq uid
        }.bindPaginationQuery(Medias, pagingFetch)
    }.mapResult { list ->
        count(backend) {
            Medias.selectAll().where {
                Medias.owner eq uid
            }
        }.mapResult { count ->
            val names = list.map {
                "${it.owner}/${it.name}"
            }
            backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, names).map { mediaUrls ->
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

suspend fun DatabaseFactory.getRawMedia(backend: Backend, owner: PrimaryKey, name: String): Result<Media?> {
    return first(backend, Media::wrapRow) {
        Medias.selectAll().where {
            Medias.owner eq owner and (Medias.name eq name)
        }
    }
}

suspend fun DatabaseFactory.getMediaInfoList(
    backend: Backend,
    owner: PrimaryKey,
): Result<List<MediaInfo?>?> {
    return mapQuery(backend, Media::wrapRow) {
        Medias.selectAll().where {
            Medias.owner eq owner
        }.orderBy(Medias.id, SortOrder.DESC)
    }.mapResultIfNotNull { medias ->
        backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, medias.map {
            it.fullName
        }).mapIfNotNull {
            medias.mapIndexed { i, e ->
                it[i]?.let { it1 -> e.toMediaInfo(it1) }
            }
        }
    }
}

suspend fun DatabaseFactory.getMediaInfoList(backend: Backend, names: List<String?>): Result<List<MediaInfo?>?> {
    if (names.filterNotNull().isEmpty()) {
        return Result.success(List(names.size) {
            null
        })
    }
    return mapQuery(backend, Media::wrapRow) {
        Medias.selectAll().where {
            Medias.fullName inList names.filterNotNull()
        }.orderBy(Medias.id, SortOrder.DESC)
    }.mapResult { medias ->
        val mediaMap = medias.associateBy { it.fullName }
        backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, names.map {
            mediaMap[it]?.fullName
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

suspend fun DatabaseFactory.copyMedia(
    backend: Backend,
    media: Media,
    newOwner: PrimaryKey,
    newName: String
): Result<ServerResponse<MediaInfo?>> {
    val id = SnowflakeFactory.nextId()
    return dbQuery(backend) {
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
        backend.mediaService.copy(
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
