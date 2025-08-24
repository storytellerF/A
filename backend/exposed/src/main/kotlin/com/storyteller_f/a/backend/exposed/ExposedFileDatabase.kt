package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.FileDatabase
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.FileRecords
import com.storyteller_f.a.backend.exposed.tables.FileRefs
import com.storyteller_f.a.backend.exposed.tables.Quotas
import com.storyteller_f.a.backend.exposed.tables.UploadRecords
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.merge
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.statements.UpsertSqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedFileDatabase(val databaseSession: ExposedDatabaseSession) : FileDatabase {
    override suspend fun getFileRecord(owner: PrimaryKey, name: String): Result<FileRecord?> {
        return databaseSession.dbSearch {
            search {
                FileRecords.selectAll().where {
                    FileRecords.owner eq owner and (FileRecords.name eq name)
                }
            }
            first(FileRecord::wrapRow)
        }
    }

    override suspend fun getFileRecordByIds(ids: List<PrimaryKey>): Result<List<FileRecord>> {
        return databaseSession.dbSearch {
            search {
                FileRecords.selectAll().where {
                    FileRecords.id inList ids
                }
            }
            map(FileRecord::wrapRow)
        }
    }

    override suspend fun getFileRecordListByOwner(owner: PrimaryKey): Result<List<FileRecord>> {
        return databaseSession.dbSearch {
            search {
                FileRecords.selectAll().where {
                    FileRecords.owner eq owner
                }.orderBy(FileRecords.id, SortOrder.DESC)
            }
            map(FileRecord::wrapRow)
        }
    }

    override suspend fun getFileRecordByNames(names: List<String?>): Result<List<FileRecord>> {
        if (names.filterNotNull().isEmpty()) {
            return Result.success(emptyList())
        }
        return databaseSession.dbSearch {
            search {
                FileRecords.selectAll().where {
                    FileRecords.fullName inList names.filterNotNull()
                }.orderBy(FileRecords.id, SortOrder.DESC)
            }
            map(FileRecord::wrapRow)
        }
    }

    override suspend fun insertFileRefs(
        objectId: PrimaryKey,
        objectType: ObjectType,
        mediaName: List<Pair<PrimaryKey, String>>,
    ): Result<Unit> {
        return databaseSession.dbQuery {
            FileRefs.batchInsert(mediaName) {
                this[FileRefs.objectId] = objectId
                this[FileRefs.objectType] = objectType
                this[FileRefs.mediaName] = it.second
                this[FileRefs.author] = it.first
            }
            Unit
        }
    }

    override suspend fun getFileRecordPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<FileRecord>> {
        return merge(
            {
                databaseSession.dbSearch {
                    search {
                        FileRecords.selectAll().where {
                            FileRecords.owner eq uid
                        }.bindPaginationQuery(FileRecords, primaryKeyFetch)
                    }
                    map(FileRecord::wrapRow)
                }
            }, {
                databaseSession.dbSearch {
                    search {
                        FileRecords.selectAll().where {
                            FileRecords.owner eq uid
                        }
                    }
                    count()
                }
            }
        ).map { (list, count) ->
            PaginationResult(list, count)
        }
    }

    override suspend fun insertFileRecord(
        fileRecordList: List<FileRecord>,
        ownerId: PrimaryKey,
        ownerType: ObjectType
    ): Result<Unit> {
        return databaseSession.dbQuery {
            FileRecords.batchInsert(fileRecordList) { e ->
                this[FileRecords.id] = e.id
                this[FileRecords.createdTime] = now()
                this[FileRecords.name] = e.name
                this[FileRecords.duration] = 0
                this[FileRecords.width] = e.width
                this[FileRecords.height] = e.height
                this[FileRecords.owner] = e.owner
                this[FileRecords.ownerType] = e.ownerType
                this[FileRecords.contentType] = e.contentType
                this[FileRecords.size] = e.size
                this[FileRecords.fullName] = e.fullName
            }
        }
    }

    override suspend fun insertUploadRecord(record: UploadRecord): Result<Unit> {
        return databaseSession.dbQuery {
            check(UploadRecords.insert {
                it[UploadRecords.id] = record.id
                it[UploadRecords.createdTime] = record.createdTime
                it[UploadRecords.objectId] = record.objectId
                it[UploadRecords.objectType] = record.objectType
                it[UploadRecords.total] = record.total
                it[UploadRecords.progress] = record.progress
                it[UploadRecords.name] = record.name
            }.insertedCount > 0) {
                "insert upload record failed"
            }
            check(Quotas.update({
                Quotas.ownerId eq record.objectId and (Quotas.quotaType eq QuotaType.FILE) and (Quotas.locking eq false)
            }) {
                it[this.locking] = true
            } > 0) {
                "lock quota failed"
            }
        }
    }

    override suspend fun deleteUploadRecord(
        id: PrimaryKey,
        quotaInfo: QuotaInfo,
        length: Long
    ): Result<Unit> {
        val ownerId = quotaInfo.ownerId
        return databaseSession.dbQuery {
            check(UploadRecords.deleteWhere {
                UploadRecords.id eq id
            } > 0) {
                "delete upload record failed"
            }
            check(Quotas.update({
                Quotas.ownerId eq ownerId and
                        (Quotas.quotaType eq QuotaType.FILE) and
                        (Quotas.locking eq true) and
                        (Quotas.used eq quotaInfo.used)
            }) {
                it[Quotas.locking] = false
                it[Quotas.used] = quotaInfo.used + length
            } > 0) {
                "unlock quota failed"
            }
        }
    }
}
