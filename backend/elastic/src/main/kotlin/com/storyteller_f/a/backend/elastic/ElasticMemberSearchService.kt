package com.storyteller_f.a.backend.elastic

import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ElasticConnection
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.MemberDocument
import com.storyteller_f.a.backend.core.service.MemberDocumentSearch
import com.storyteller_f.a.backend.core.service.MemberSearchService
import com.storyteller_f.a.backend.core.service.MemberSearchServiceFactory
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.future.await

class ElasticMemberSearchService(connection: ElasticConnection) : Elastic(connection),
    MemberSearchService {
    companion object {
        const val INDEX_NAME = "members"
    }

    override suspend fun saveDocument(documents: List<MemberDocument>): Result<Unit> {
        return useElasticClient {
            saveDocumentList(connection, documents, INDEX_NAME)
        }
    }

    override suspend fun deleteDocument(uid: PrimaryKey, objectId: PrimaryKey): Result<Unit> {
        return useElasticClient {
            val query = QueryBuilders.bool { b ->
                b.must { m ->
                    m.term { t ->
                        t.field("uid").value(uid.toString())
                    }
                }.must { m ->
                    m.term { t ->
                        t.field("objectId").value(objectId.toString())
                    }
                }
            }

            val response = deleteByQuery { d ->
                d.index(INDEX_NAME).query(query).refresh(connection.refresh)
            }.await()

            Napier.d {
                "elastic delete member document: query=$query, deleted=${response.deleted()}"
            }
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useElasticClient {
            cleanAll(INDEX_NAME)
        }
    }

    override suspend fun searchDocument(
        memberDocumentSearch: MemberDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<MemberDocument>> {
        val boolQuery = buildSearchQuery(memberDocumentSearch, primaryKeyFetch)

        val request = SearchRequest.of { s ->
            s.index(INDEX_NAME)
                .query(boolQuery)
                .size(primaryKeyFetch?.size ?: 10)
                .sort { sort ->
                    sort.field { f ->
                        val sortOrder = when {
                            primaryKeyFetch == null || primaryKeyFetch.cursor == null -> null
                            primaryKeyFetch.cursor is Cursor.AscCursor<PrimaryKey> -> SortOrder.Asc
                            else -> null
                        } ?: SortOrder.Desc
                        f.field("id").order(sortOrder)
                    }
                }
        }
        Napier.i {
            "elastic search member query $request"
        }
        return useElasticClient {
            searchDocumentList(request, MemberDocument::class.java)
        }
    }

    private fun buildSearchQuery(
        memberDocumentSearch: MemberDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Query {
        return buildPrimaryKeyElasticSearchQuery(primaryKeyFetch) {
            when (memberDocumentSearch) {
                is MemberDocumentSearch.Keyword -> {
                    // 按 objectId 搜索
                    memberDocumentSearch.objectId?.let { objId ->
                        add(QueryBuilders.term { t ->
                            t.field("objectId").value(objId)
                        } to true)
                    }
                    // 按 nickname 搜索
                    memberDocumentSearch.nickname?.let { nickname ->
                        preprocessUserInputKeyword(listOf(nickname))?.let { v ->
                            add(QueryBuilders.multiMatch { m ->
                                m.fields("nickname").query(v)
                            } to true)
                        }
                    }
                }

                is MemberDocumentSearch.CommunityMembers -> {
                    // 按 uid 搜索（该用户加入的社区）
                    add(QueryBuilders.term { t ->
                        t.field("uid").value(memberDocumentSearch.uid)
                    } to true)
                    // 按 objectType 搜索（只搜索社区）
                    add(QueryBuilders.term { t ->
                        t.field("objectType.keyword").value(ObjectType.COMMUNITY.name)
                    } to true)
                    // 按社区名称前缀搜索
                    add(QueryBuilders.matchPhrasePrefix { p ->
                        p.field("objectName").query(memberDocumentSearch.objectName).boost(2f)
                    } to true)
                }

                is MemberDocumentSearch.RoomMembers -> {
                    // 按 uid 搜索（该用户加入的房间）
                    add(QueryBuilders.term { t ->
                        t.field("uid").value(memberDocumentSearch.uid)
                    } to true)
                    // 按 objectType 搜索（只搜索房间）
                    add(QueryBuilders.term { t ->
                        t.field("objectType.keyword").value(ObjectType.ROOM.name)
                    } to true)
                    // 按房间名称前缀搜索
                    add(QueryBuilders.matchPhrasePrefix { p ->
                        p.field("objectName").query(memberDocumentSearch.objectName).boost(2f)
                    } to true)
                    // 添加对communityId的过滤
                    memberDocumentSearch.communityId?.let { communityId ->
                        add(QueryBuilders.term { t ->
                            t.field("communityId").value(communityId)
                        } to true)
                    }
                }
            }
        }
    }
}

class ElasticMemberSearchServiceFactory : MemberSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "elastic"
    }

    override fun build(env: MergedEnv): MemberSearchService {
        return buildElasticSearchService(env) {
            ElasticMemberSearchService(it)
        }
    }
}
