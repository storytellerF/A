package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.FileDatabase
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.firstNotNull
import com.storyteller_f.a.backend.exposed.map
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
import com.storyteller_f.shared.type.UploadRecordStatus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedFileDatabase(val databaseSession: ExposedDatabaseSession) : FileDatabase {
    override suspend fun getUploadRecord(id: PrimaryKey) = databaseSession.dbSearch {
        search {
            UploadRecords.selectAll().where {
                UploadRecords.id eq id
            }
        }
        first(UploadRecord::wrapRow)
    }

    override suspend fun getFileRecord(owner: PrimaryKey, name: String) = databaseSession.dbSearch {
        search {
            FileRecords.selectAll().where {
                FileRecords.owner eq owner and (FileRecords.name eq name)
            }
        }
        first(FileRecord::wrapRow)
    }

    override suspend fun getFileRecordByIds(ids: List<PrimaryKey>): Result<List<FileRecord>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        return databaseSession.dbSearch {
            search {
                FileRecords.selectAll().where {
                    FileRecords.id inList ids
                }
            }
            map(FileRecord::wrapRow)
        }
    }

    override suspend fun getFileRecordListByOwner(owner: PrimaryKey) = databaseSession.dbSearch {
        search {
            FileRecords.selectAll().where {
                FileRecords.owner eq owner
            }.orderBy(FileRecords.id, SortOrder.DESC)
        }
        map(FileRecord::wrapRow)
    }

    override suspend fun getFileRecordByNames(names: List<String>): Result<List<FileRecord>> {
        if (names.isEmpty()) {
            return Result.success(emptyList())
        }
        return databaseSession.dbSearch {
            search {
                FileRecords.selectAll().where {
                    FileRecords.fullName inList names
                }.orderBy(FileRecords.id, SortOrder.DESC)
            }
            map(FileRecord::wrapRow)
        }
    }

    override suspend fun insertFileRefs(
        objectId: PrimaryKey,
        objectType: ObjectType,
        mediaName: List<Pair<PrimaryKey, String>>,
    ) = databaseSession.dbQuery {
        check(FileRefs.batchInsert(mediaName) {
            this[FileRefs.objectId] = objectId
            this[FileRefs.objectType] = objectType
            this[FileRefs.mediaName] = it.second
            this[FileRefs.author] = it.first
        }.size == mediaName.size) {
            "insert file refs failed"
        }
    }

    override suspend fun getFileRecordPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ) = runCatching {
        val r1 = getFileRecordListByPredicate {
            where { FileRecords.owner eq uid }.bindPaginationQuery(FileRecords, primaryKeyFetch)
        }.getOrThrow()
        val r2 = getFileRecordCountByPredicate { where { FileRecords.owner eq uid } }.getOrThrow()
        PaginationResult(r1, r2)
    }

    override suspend fun getAllFileRecordPaginationList(primaryKeyFetch: PrimaryKeyFetch) = runCatching {
        val r1 = getFileRecordListByPredicate { bindPaginationQuery(FileRecords, primaryKeyFetch) }.getOrThrow()
        val r2 = getFileRecordCountByPredicate { this }.getOrThrow()
        PaginationResult(r1, r2)
    }

    override suspend fun insertFileRecord(
        fileRecordList: List<FileRecord>,
        ownerId: PrimaryKey,
        ownerType: ObjectType
    ) = databaseSession.dbQuery {
        check(FileRecords.batchInsert(fileRecordList) { e ->
            this[FileRecords.id] = e.id
            this[FileRecords.createdTime] = e.createdTime
            this[FileRecords.name] = e.name
            this[FileRecords.duration] = 0
            this[FileRecords.width] = e.width
            this[FileRecords.height] = e.height
            this[FileRecords.owner] = e.owner
            this[FileRecords.ownerType] = e.ownerType
            this[FileRecords.contentType] = e.contentType
            this[FileRecords.size] = e.size
            this[FileRecords.fullName] = e.fullName
        }.size == fileRecordList.size) {
            "insert file record failed"
        }
    }

    override suspend fun insertUploadRecord(record: UploadRecord): Result<UploadRecord> = databaseSession.dbQuery {
        check(UploadRecords.insert {
            it[UploadRecords.id] = record.id
            it[UploadRecords.createdTime] = record.createdTime
            it[UploadRecords.objectId] = record.objectId
            it[UploadRecords.objectType] = record.objectType
            it[UploadRecords.status] = record.status
            it[UploadRecords.total] = record.total
            it[UploadRecords.progress] = record.progress
            it[UploadRecords.name] = record.name
            it[UploadRecords.chunkSize] = record.chunkSize
        }.insertedCount > 0) {
            "insert upload record failed"
        }
        check(Quotas.update({
            Quotas.ownerId eq record.objectId and
                (Quotas.quotaType eq QuotaType.FILE) and
                (Quotas.lockId eq null)
        }) {
            it[this.lockId] = record.id
        } > 0) {
            "lock quota failed"
        }
        record
    }

    override suspend fun updateUploadRecordStatus(
        quotaInfo: QuotaInfo,
        record: UploadRecord,
        fileRecordList: List<FileRecord>,
    ): Result<List<FileRecord>> {
        if (record.status == UploadRecordStatus.PENDING) {
            return Result.failure(CustomBadRequestException("can't update pending record"))
        }
        return databaseSession.dbQuery {
            check(UploadRecords.update({
                UploadRecords.id eq record.id
            }) {
                it[UploadRecords.status] = record.status
            } > 0) {
                "delete upload record failed"
            }
            check(Quotas.update({
                Quotas.ownerId eq quotaInfo.ownerId and
                    (Quotas.quotaType eq QuotaType.FILE) and
                    (Quotas.lockId eq record.id) and
                    (Quotas.used eq quotaInfo.used)
            }) {
                it[Quotas.lockId] = null
                if (record.status != UploadRecordStatus.SUCCESS) {
                    it[Quotas.used] = quotaInfo.used
                } else {
                    it[Quotas.used] = quotaInfo.used + record.total
                }
            } > 0) {
                "unlock quota failed"
            }
            check(FileRecords.batchInsert(fileRecordList) { e ->
                this[FileRecords.id] = e.id
                this[FileRecords.createdTime] = e.createdTime
                this[FileRecords.name] = e.name
                this[FileRecords.duration] = 0
                this[FileRecords.width] = e.width
                this[FileRecords.height] = e.height
                this[FileRecords.owner] = e.owner
                this[FileRecords.ownerType] = e.ownerType
                this[FileRecords.contentType] = e.contentType
                this[FileRecords.size] = e.size
                this[FileRecords.fullName] = e.fullName
            }.size == fileRecordList.size) {
                "insert file record failed"
            }
            fileRecordList
        }
    }

    override suspend fun getFileCount() = databaseSession.dbSearch {
        search {
            FileRecords.selectAll()
        }
        count()
    }

    override suspend fun getFileVolume() = databaseSession.dbSearch {
        val sumColumn = FileRecords.size.sum()
        search {
            FileRecords.select(sumColumn)
        }
        firstNotNull {
            it[sumColumn] ?: 0
        }
    }

    private suspend fun getFileRecordListByPredicate(
        queryBuilder: Query.() -> Query = { this }
    ) = databaseSession.dbSearch {
        search {
            FileRecords.selectAll().queryBuilder()
        }
        map(FileRecord::wrapRow)
    }

    private suspend fun getFileRecordCountByPredicate(
        queryBuilder: Query.() -> Query = { this }
    ) = databaseSession.dbSearch {
        search {
            FileRecords.selectAll().queryBuilder()
        }
        count()
    }
}
