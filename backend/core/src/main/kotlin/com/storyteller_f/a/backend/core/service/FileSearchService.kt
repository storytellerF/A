package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.PrimaryKey

data class FileDocument(
    override val id: PrimaryKey,
    val name: String,
    val ownerId: PrimaryKey
) : PrimaryKeyIdentifiable {
    companion object {
        fun fromFileRecord(fileRecord: FileRecord): FileDocument {
            return FileDocument(fileRecord.id, fileRecord.name, fileRecord.owner)
        }
    }
}

sealed interface FileDocumentSearch {
    data class Keyword(
        val word: List<String>? = null,
        val ownerId: PrimaryKey? = null
    ) : FileDocumentSearch
}

interface FileSearchService {
    suspend fun saveDocument(documents: List<FileDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        fileDocumentSearch: FileDocumentSearch = FileDocumentSearch.Keyword(),
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<FileDocument>>
}

interface FileSearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): FileSearchService
}
