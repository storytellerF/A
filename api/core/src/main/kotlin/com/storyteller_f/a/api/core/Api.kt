package com.storyteller_f.a.api.core

import com.storyteller_f.a.api.core.Api.Communities.Id.Topics.Query
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

interface PageableQuery {
    val pagination: PaginationQuery
}

class PaginationQuery(val nextPageToken: String? = null, val prePageToken: String? = null, val size: Int)

object Api {
    object Topics {
        object Aid {
            @Serializable
            class AidQuery(val aid: String, val fillHasCommented: Boolean? = null)

            val get = safeApiWithQuery<TopicInfo, AidQuery>("topics/aid")
        }

        object Search {
            @Serializable
            class Query(
                val word: List<String>? = null,
                val parentId: PrimaryKey? = null,
                val parentType: ObjectType? = null,
                val nextPageToken: String? = null,
                val size: Int = 10
            ) : PageableQuery {

                override val pagination: PaginationQuery
                    get() = PaginationQuery(nextPageToken, size = size)
            }

            val get = safeApiWithQuery<ServerResponse<TopicInfo>, Query>("topics/search")
        }

        object Recommend {
            @Serializable
            class Query(val fillHasCommented: Boolean? = null)

            val get = safeApi<ServerResponse<TopicInfo>>("topics/recommend")
        }

        object Id {
            @Serializable
            class Query(val fillHasCommented: Boolean? = null)

            val get = safeApiWithQueryAndPath<TopicInfo, Query, Path>("topics/{id}")

            object Topics {
                @Serializable
                class Query(
                    val pinType: TopicPinSearch? = null,
                    val fillHasCommented: Boolean? = null,
                    val prePageToken: String? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10,
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() {
                            return PaginationQuery(nextPageToken, prePageToken, size)
                        }
                }

                val get = safeApiWithQueryAndPath<ServerResponse<TopicInfo>, Query, Path>("topics/{id}/topics")
            }
            object Reactions {
                @Serializable
                class Query(
                    val fillHasReacted: Boolean? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, size = size)
                }
                val get = safeApiWithQueryAndPath<ServerResponse<ReactionInfo>, Query, Path>("topics/{id}/reactions")
            }
        }
    }

    object Root {
        val get = safeApi<String>("/")
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
            ) : PageableQuery {
                override val pagination: PaginationQuery
                    get() = PaginationQuery(nextPageToken, size = size)
            }

            val get = safeApiWithQuery<ServerResponse<CommunityInfo>, Query>("communities/search")
        }

        object Aid {
            @Serializable
            class Query(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeApiWithQuery<CommunityInfo, Query>("communities/aid")
        }

        object Id {
            @Serializable
            class Query(val fillJoinInfo: Boolean = false)

            val get = safeApiWithQueryAndPath<CommunityInfo, Query, Path>("communities/{id}")

            object Members {
                @Serializable
                class Query(val word: String? = null, val nextPageToken: String? = null, val size: Int = 10)

                val get =
                    safeApiWithQueryAndPath<ServerResponse<UserInfo>, Query, Path>("communities/{id}/members")
            }

            object Topics {
                @Serializable
                class Query(
                    val pinType: TopicPinSearch? = null,
                    val fillHasCommented: Boolean? = null,
                    val prePageToken: String? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10,
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, prePageToken, size)

                    constructor(
                        pinType: TopicPinSearch? = null,
                        fillHasCommented: Boolean? = null,
                        paginationQuery: PaginationQuery
                    ) : this(
                        pinType,
                        fillHasCommented,
                        paginationQuery.prePageToken,
                        paginationQuery.nextPageToken,
                        paginationQuery.size
                    )
                }

                val get = safeApiWithQueryAndPath<ServerResponse<TopicInfo>, Query, Path>("communities/{id}/topics")
            }
        }
    }

    object Rooms {
        object Search {
            @Serializable
            data class Query(
                val joinStatus: JoinStatusSearch? = null,
                val word: String? = null,
                val community: PrimaryKey? = null,
                val nextPageToken: String? = null,
                val size: Int = 10
            ) : PageableQuery {
                override val pagination: PaginationQuery
                    get() = PaginationQuery(nextPageToken, size = size)
            }

            val get = safeApiWithQuery<ServerResponse<RoomInfo>, Query>("rooms/search")
        }

        object Id {
            @Serializable
            class Query(val fillJoinInfo: Boolean = false)
            val get = safeApiWithQueryAndPath<RoomInfo, Query, Path>("rooms/{id}")
            object Members {
                @Serializable
                class Query(val word: String? = null)

                val get = safeApiWithQueryAndPath<ServerResponse<UserInfo>, Query, Path>("rooms/{id}/members")
            }

            object Topics {
                @Serializable
                class Query(
                    val pinType: TopicPinSearch? = null,
                    val fillHasCommented: Boolean? = null,
                    val prePageToken: String? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10,
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, prePageToken, size)
                }
                val get = safeApiWithQueryAndPath<ServerResponse<TopicInfo>, Query, Path>("rooms/{id}/topics")
            }
        }

        object Aid {
            @Serializable
            class Query(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeApiWithQuery<RoomInfo, Query>("rooms/aid")
        }
    }

    object Users {
        object Aid {
            @Serializable
            class Query(val aid: String)

            val get = safeApiWithQuery<UserInfo, Query>("users/aid")
        }

        object Id {
            val get = safeApiWithPath<UserInfo, Path>("users/{id}")

            object Topics {
                @Serializable
                class Query(
                    val fillHasCommented: Boolean? = null,
                    val pinType: TopicPinSearch? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, size = size)
                }

                val get = safeApiWithQueryAndPath<ServerResponse<TopicInfo>, Query, Path>("users/{id}/topics")
            }

            object Titles {
                @Serializable
                class Query(
                    val searchType: TitleSearchType,
                    val type: TitleType? = null,
                    val scopeId: PrimaryKey? = null,
                    val status: PrimaryKey? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, size = size)
                }

                val get = safeApiWithQueryAndPath<ServerResponse<TitleInfo>, Query, Path>("users/{id}/titles")
            }
        }

        object Search {
            @Serializable
            class Query(val word: String, val nextPageToken: String? = null, val size: Int = 10) : PageableQuery {
                override val pagination: PaginationQuery
                    get() = PaginationQuery(nextPageToken, size = size)
            }

            val get = safeApiWithQuery<ServerResponse<UserInfo>, Query>("users/search")
        }
    }

    object Medias {
        @Serializable
        class Query(
            val objectId: PrimaryKey? = null,
            val objectType: ObjectType? = null,
            val nextPageToken: String? = null,
            val size: Int = 10
        ) : PageableQuery {
            override val pagination: PaginationQuery
                get() = PaginationQuery(nextPageToken, size = size)
        }
        val get = safeApiWithQuery<ServerResponse<MediaInfo>, Query>("amedia")
    }
}

enum class MutationMethodType {
    POST, PUT, PATCH, DELETE
}

enum class SafeMethodType {
    GET, OPTIONS
}

sealed interface AbstractApi<Resp> {
    val urlString: String
}

interface AbstractMutationApi<Resp> : AbstractApi<Resp> {
    val methodType: MutationMethodType
}

interface AbstractSafeApi<Resp> : AbstractApi<Resp> {
    val methodType: SafeMethodType
}

class AidQuery(val aid: String)

@Serializable
class Path(val id: PrimaryKey)

class SafeApi<Resp : Any>(
    override val urlString: String,
    override val methodType: SafeMethodType,
) : AbstractSafeApi<Resp>

class SafeApiWithQuery<Resp : Any, Query : Any>(
    override val urlString: String,
    val queryClass: KClass<Query>,
    override val methodType: SafeMethodType,
) : AbstractSafeApi<Resp>

class SafeApiWithPath<Resp : Any, PathQuery : Any>(
    override val urlString: String,
    val pathClass: KClass<PathQuery>,
    override val methodType: SafeMethodType
) : AbstractSafeApi<Resp>

class SafeApiWithQueryAndPath<Resp : Any, Query : Any, PathQuery : Any>(
    override val urlString: String,
    val queryClass: KClass<Query>,
    val pathClass: KClass<PathQuery>,
    override val methodType: SafeMethodType
) : AbstractSafeApi<Resp>

inline fun <Resp : Any, reified Query : Any, reified PathQuery : Any> safeApiWithQueryAndPath(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApiWithQueryAndPath<Resp, Query, PathQuery> {
    return SafeApiWithQueryAndPath(
        path,
        Query::class,
        PathQuery::class,
        methodType
    )
}

inline fun <Resp : Any, reified Query : Any> safeApiWithQuery(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApiWithQuery<Resp, Query> {
    return SafeApiWithQuery(
        path,
        Query::class,
        methodType
    )
}

inline fun <Resp : Any, reified Path : Any> safeApiWithPath(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApiWithPath<Resp, Path> = SafeApiWithPath(
    path,
    Path::class,
    methodType
)

fun <Resp : Any> safeApi(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApi<Resp> {
    return SafeApi(
        path,
        methodType
    )
}
