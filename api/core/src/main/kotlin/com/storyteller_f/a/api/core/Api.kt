package com.storyteller_f.a.api.core

import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PosterSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object Api {
    object Topics {
        object Aid {
            val get = ApiGetWithQuery<TopicInfo, AidQuery>("topics/aid", AidQuery::class)
        }
    }

    object Root {
        val get = ApiGet<String>("/")
    }

    object Communities {
        object Search {
            @Serializable
            data class Query(
                val joinStatus: JoinStatusSearch? = null,
                val word: String? = null,
                val target: PrimaryKey? = null,
                val hasPoster: PosterSearch? = null,
                val nextPageToken: String? = null,
                val size: Int = 10,
            )

            val get = ApiGetWithQuery<ServerResponse<CommunityInfo>, Query>(
                "communities/search",
                Query::class,
            )
        }

        object Id {
            @Serializable
            class Path(val id: PrimaryKey)

            @Serializable
            class Query(val fillJoinInfo: Boolean = false)

            val get = ApiGetWithQueryAndPath<CommunityInfo, Query, Path>("communities/{id}", Query::class, Path::class)

            object Members {
                @Serializable
                class Path(val id: PrimaryKey)

                @Serializable
                class Query(val word: String? = null, val nextPageToken: String? = null, val size: Int = 10)

                val get =
                    ApiGetWithQueryAndPath<ServerResponse<UserInfo>, Query, Path>(
                        "communities/{id}/members",
                        Query::class,
                        Path::class,
                    )
            }
        }
    }
}

interface AbstractApi<Resp> {
    val urlString: String
}

interface AbstractApiGet<Resp, Parameters> : AbstractApi<Resp>

class AidQuery(val aid: String)

class ApiGet<Resp : Any>(
    override val urlString: String,
) : AbstractApi<Resp>

class ApiGetWithQuery<Resp : Any, Query : Any>(
    override val urlString: String,
    val queryClass: KClass<Query>,
) : AbstractApiGet<Resp, Query>

class ApiGetWithPath<Resp : Any, PathQuery : Any>(
    override val urlString: String,
    val pathClass: KClass<PathQuery>
) : AbstractApiGet<Resp, PathQuery>

class ApiGetWithQueryAndPath<Resp : Any, Query : Any, PathQuery : Any>(
    override val urlString: String,
    val queryClass: KClass<Query>,
    val pathClass: KClass<PathQuery>
) : AbstractApiGet<Resp, Pair<Query, PathQuery>>

class ApiPost<Body : Any, Resp : Any>(val path: String, val bodyClass: KClass<Body>, val respClass: KClass<Resp>)
