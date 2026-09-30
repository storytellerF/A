/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.OffsetFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class UserDocument(override val id: PrimaryKey, val nickname: String, val aid: String?) :
    PrimaryKeyIdentifiable {
    companion object {
        fun fromUser(user: User): UserDocument = UserDocument(user.id, user.nickname, user.aid)
    }
}

@Serializable
sealed interface UserDocumentSearch {
    @Serializable
    data class Keyword(val word: String, val fetch: OffsetFetch) : UserDocumentSearch
}

interface UserSearchService {
    suspend fun saveDocument(documents: List<UserDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(userDocumentSearch: UserDocumentSearch): Result<PaginationResult<UserDocument>>
}

interface UserSearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): UserSearchService
}
