package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.exposed.query.PaginationResult
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.Media
import com.storyteller_f.a.backend.exposed.tables.MediaRefs
import com.storyteller_f.a.backend.exposed.tables.Medias
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll

class ExposedMediaDatabase(val exposedDatabaseSession: ExposedDatabaseSession) : MediaDatabase {
    override suspend fun getMedia(owner: PrimaryKey, name: String): Result<Media?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.owner eq owner and (Medias.name eq name)
                }
            }
            first(Media::wrapRow)
        }
    }

    override suspend fun getMediaByIds(ids: List<PrimaryKey>): Result<List<Media>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.id inList ids
                }
            }
            map(Media::wrapRow)
        }
    }

    override suspend fun getMediaListByOwner(owner: PrimaryKey): Result<List<Media>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.owner eq owner
                }.orderBy(Medias.id, SortOrder.DESC)
            }
            map(Media::wrapRow)
        }
    }

    override suspend fun getMediaByNames(names: List<String?>): Result<List<Media>> {
        if (names.filterNotNull().isEmpty()) {
            return Result.success(emptyList())
        }
        return exposedDatabaseSession.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.fullName inList names.filterNotNull()
                }.orderBy(Medias.id, SortOrder.DESC)
            }
            map(Media::wrapRow)
        }
    }

    override suspend fun insertMediaRefs(
        objectId: PrimaryKey,
        objectType: ObjectType,
        mediaName: List<Pair<PrimaryKey, String>>,
    ): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            MediaRefs.batchInsert(mediaName) {
                this[MediaRefs.objectId] = objectId
                this[MediaRefs.objectType] = objectType
                this[MediaRefs.mediaName] = it.second
                this[MediaRefs.author] = it.first
            }
            Unit
        }
    }

    override suspend fun getMediaPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<Media>> {
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
            }.map { count ->
                PaginationResult(list, count)
            }
        }
    }

    override suspend fun insertMedia(
        mediaList: List<Media>
    ) {
        exposedDatabaseSession.dbQuery {
            Media.insertMediaList(mediaList)
        }
    }

    override suspend fun insertCopiedMedia(newId: PrimaryKey, media: Media, newOwner: PrimaryKey): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            Media.insertCopiedMedia(newId, media, newOwner)
        }
    }
}
