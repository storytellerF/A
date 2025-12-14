package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.OffsetFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.PrimaryKey

data class CommunityDocument(
    override val id: PrimaryKey,
    val name: String,
    val aid: String,
    val owner: PrimaryKey,
) : PrimaryKeyIdentifiable {
    companion object {
        fun fromCommunity(community: Community): CommunityDocument {
            return CommunityDocument(community.id, community.name, community.aid, community.owner)
        }
    }
}

sealed interface CommunityDocumentSearch {
    data class Keyword(
        val keyword: String,
        val fetch: OffsetFetch
    ) : CommunityDocumentSearch
}

interface CommunitySearchService {
    suspend fun saveDocument(documents: List<CommunityDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        communityDocumentSearch: CommunityDocumentSearch
    ): Result<PaginationResult<CommunityDocument>>
}

interface CommunitySearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): CommunitySearchService
}
