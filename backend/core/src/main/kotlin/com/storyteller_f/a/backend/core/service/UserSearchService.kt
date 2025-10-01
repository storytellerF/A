package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

data class UserDocument(override val id: PrimaryKey, val nickname: String, val aid: String?) :
    PrimaryKeyIdentifiable {
    override val objectType: ObjectType
        get() = ObjectType.USER

    companion object {
        fun fromUser(user: User): UserDocument {
            return UserDocument(user.id, user.nickname, user.aid)
        }
    }
}

sealed interface UserDocumentSearch {
    data class Keyword(val word: List<String>? = null) : UserDocumentSearch
}

interface UserSearchService {
    suspend fun saveDocument(documents: List<UserDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        userDocumentSearch: UserDocumentSearch = UserDocumentSearch.Keyword(),
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<UserDocument>>
}

interface UserSearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): UserSearchService
}
