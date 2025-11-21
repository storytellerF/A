package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

data class MemberDocument(
    override val id: PrimaryKey,
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val nickname: String,
    val objectName: String,
) : PrimaryKeyIdentifiable {
    companion object {
        fun fromUserInfo(
            id: PrimaryKey,
            userInfo: UserInfo,
            objectId: PrimaryKey,
            objectType: ObjectType,
            objectName: String
        ): MemberDocument {
            return MemberDocument(
                id = id,
                uid = userInfo.id,
                objectId = objectId,
                objectType = objectType,
                nickname = userInfo.nickname,
                objectName = objectName
            )
        }
    }
}

sealed interface MemberDocumentSearch {
    data class Keyword(
        val objectId: PrimaryKey? = null,
        val nickname: String? = null
    ) : MemberDocumentSearch

    data class CommunityMembers(
        val uid: PrimaryKey,
        val objectName: String
    ) : MemberDocumentSearch

    data class RoomMembers(
        val uid: PrimaryKey,
        val objectName: String
    ) : MemberDocumentSearch
}

interface MemberSearchService {
    suspend fun saveDocument(documents: List<MemberDocument>): Result<Unit>

    suspend fun deleteDocument(uid: PrimaryKey, objectId: PrimaryKey): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        memberDocumentSearch: MemberDocumentSearch = MemberDocumentSearch.Keyword(),
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<MemberDocument>>
}

interface MemberSearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): MemberSearchService
}
