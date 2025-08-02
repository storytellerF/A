package com.storyteller_f.a.api.core

import com.storyteller_f.route4k.common.*
import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

interface PageableQuery {
    val pagination: PaginationQuery
}

@Serializable
class PaginationQuery(val nextPageToken: String? = null, val prePageToken: String? = null, val size: Int)

object CustomApi {
    object Topics {
        object Aid {
            @Serializable
            class TopicAidQuery(val aid: String, val fillHasCommented: Boolean? = null)

            val get = safeApiWithQuery<TopicInfo, TopicAidQuery>("topics/aid")
        }

        @Serializable
        class TopicSearchQuery(
            val word: List<String>? = null,
            val parentId: PrimaryKey? = null,
            val parentType: ObjectType? = null,
            val nextPageToken: String? = null,
            val size: Int = 10,
        ) : PageableQuery {

            override val pagination: PaginationQuery
                get() = PaginationQuery(nextPageToken, size = size)
        }

        val search = safeApiWithQuery<ServerResponse<TopicInfo>, TopicSearchQuery>("topics/search")

        @Serializable
        class RecommendQuery(
            val fillHasCommented: Boolean? = null,
            val nextPageToken: String? = null,
            val size: Int = 10,
        ) : PageableQuery {
            override val pagination: PaginationQuery
                get() = PaginationQuery(nextPageToken, size = size)
        }

        val recommend = safeApiWithQuery<ServerResponse<TopicInfo>, RecommendQuery>("topics/recommend")

        object Id {
            @Serializable
            class TopicIdQuery(val fillHasCommented: Boolean? = null)

            val get = safeApiWithQueryAndPath<TopicInfo, TopicIdQuery, Path>("topics/{id}")

            object Topics {
                val get = safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, Path>("topics/{id}/topics")
            }

            object Reactions {
                @Serializable
                class ReactionQuery(
                    val fillHasReacted: Boolean? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10,
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, size = size)
                }

                val get =
                    safeApiWithQueryAndPath<ServerResponse<ReactionInfo>, ReactionQuery, Path>("topics/{id}/reactions")
                val add = mutationApiWithPath<ReactionInfo, NewReaction, Path>("topics/{id}/reactions")
                val delete =
                    mutationApiWithPath<ReactionInfo, DeleteReaction, Path>(
                        "topics/{id}/reactions",
                        methodType = MutationMethodType.DELETE
                    )
            }

            val pin = mutationApiWithPath<TopicInfo, Unit, Path>("topics/{id}/pin")
            val unpin =
                mutationApiWithPath<TopicInfo, Unit, Path>("topics/{id}/pin", methodType = MutationMethodType.DELETE)
            val createSnapshot = mutationApiWithPath<MediaInfo, Unit, Path>("topics/{id}/create-snapshot")
        }

        val add = mutationApi<TopicInfo, NewTopic>("topics")
    }

    object Root {
        val get = safeApi<String>("/")
    }

    object Communities {
        @Serializable
        data class CommunitySearchQuery(
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

        val search = safeApiWithQuery<ServerResponse<CommunityInfo>, CommunitySearchQuery>("communities/search")

        object Aid {
            @Serializable
            class CommunityAidQuery(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeApiWithQuery<CommunityInfo, CommunityAidQuery>("communities/aid")
        }

        object Id {
            @Serializable
            class CommunityIdQuery(val fillJoinInfo: Boolean = false)

            val get = safeApiWithQueryAndPath<CommunityInfo, CommunityIdQuery, Path>("communities/{id}")

            object Members {
                @Serializable
                class CommunityMemberQuery(
                    val word: String? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10,
                )

                val get =
                    safeApiWithQueryAndPath<ServerResponse<UserInfo>, CommunityMemberQuery, Path>(
                        "communities/{id}/members"
                    )
                val join = mutationApiWithPath<CommunityInfo, Unit, Path>("communities/{id}/members")
                val leave =
                    mutationApiWithPath<CommunityInfo, Unit, Path>(
                        "communities/{id}/members",
                        methodType = MutationMethodType.DELETE
                    )
            }

            object Topics {
                val get =
                    safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, Path>("communities/{id}/topics")
            }

            val update = mutationApiWithPath<CommunityInfo, UpdateCommunityBody, Path>("communities/{id}")
        }

        val add = mutationApi<CommunityInfo, NewCommunity>("communities")
    }

    object Rooms {
        @Serializable
        data class RoomSearchQuery(
            val joinStatus: JoinStatusSearch? = null,
            val word: String? = null,
            val community: PrimaryKey? = null,
            val nextPageToken: String? = null,
            val size: Int = 10,
        ) : PageableQuery {
            override val pagination: PaginationQuery
                get() = PaginationQuery(nextPageToken, size = size)
        }

        val search = safeApiWithQuery<ServerResponse<RoomInfo>, RoomSearchQuery>("rooms/search")

        object Id {
            @Serializable
            class RoomIdQuery(val fillJoinInfo: Boolean = false)

            val get = safeApiWithQueryAndPath<RoomInfo, RoomIdQuery, Path>("rooms/{id}")

            object Members {
                @Serializable
                class MemberQuery(val word: String? = null, val nextPageToken: String? = null, val size: Int = 10) :
                    PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, size = size)
                }

                val get = safeApiWithQueryAndPath<ServerResponse<UserInfo>, MemberQuery, Path>("rooms/{id}/members")
                val join =
                    mutationApiWithPath<RoomInfo, Unit, Path>(
                        "rooms/{id}/members",
                        methodType = MutationMethodType.POST
                    )
                val leave =
                    mutationApiWithPath<RoomInfo, Unit, Path>(
                        "rooms/{id}/members",
                        methodType = MutationMethodType.DELETE
                    )
                val publicKeys =
                    safeApiWithQueryAndPath<ServerResponse<UserPubKeyInfo>, PaginationQuery, Path>(
                        "rooms/{id}/public-keys"
                    )
            }

            object Topics {
                val get = safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, Path>("rooms/{id}/topics")
            }

            val update = mutationApiWithPath<RoomInfo, UpdateRoomBody, Path>("rooms/{id}/update")
        }

        object Aid {
            @Serializable
            class RoomAidQuery(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeApiWithQuery<RoomInfo, RoomAidQuery>("rooms/aid")
        }

        val add = mutationApi<RoomInfo, NewRoom>("rooms")
    }

    object Users {
        object Aid {
            @Serializable
            class UserAidQuery(val aid: String)

            val get = safeApiWithQuery<UserInfo, UserAidQuery>("users/aid")
        }

        object Id {
            val get = safeApiWithPath<UserInfo, Path>("users/{id}")

            object Topics {
                val get = safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, Path>("users/{id}/topics")
            }

            object Titles {
                @Serializable
                class TitleQuery(
                    val searchType: TitleSearchType,
                    val type: TitleType? = null,
                    val scopeId: PrimaryKey? = null,
                    val status: TitleStatus? = null,
                    val nextPageToken: String? = null,
                    val size: Int = 10,
                ) : PageableQuery {
                    override val pagination: PaginationQuery
                        get() = PaginationQuery(nextPageToken, size = size)
                }

                val get = safeApiWithQueryAndPath<ServerResponse<TitleInfo>, TitleQuery, Path>("users/{id}/titles")
            }
        }

        @Serializable
        class UserSearchQuery(
            val word: String? = null,
            val nextPageToken: String? = null,
            val size: Int = 10,
        ) : PageableQuery {
            override val pagination: PaginationQuery
                get() = PaginationQuery(nextPageToken, size = size)
        }

        val search = safeApiWithQuery<ServerResponse<UserInfo>, UserSearchQuery>("users/search")

        val update = mutationApi<UserInfo, UpdateUserBody>("users/update")

        object Read {
            val add = mutationApi<Unit, UpdateUserRead>("users/read")
        }

        object Devices {
            val add = mutationApi<Unit, NewDevice>("users/devices")
        }
    }

    object Medias {
        @Serializable
        class MediaQuery(
            val objectId: PrimaryKey? = null,
            val objectType: ObjectType? = null,
            val nextPageToken: String? = null,
            val size: Int = 10,
        ) : PageableQuery {
            override val pagination: PaginationQuery
                get() = PaginationQuery(nextPageToken, size = size)
        }

        val get = safeApiWithQuery<ServerResponse<MediaInfo>, MediaQuery>("medias")

        object Id {
            val copy = mutationApiWithPath<ServerResponse<MediaInfo>, Unit, Path>("medias/{id}/copy")
            val get = safeApiWithPath<MediaInfo, Path>("medias/{id}")
            val delete = mutationApiWithPath<Boolean, Unit, Path>("medias/{id}", methodType = MutationMethodType.DELETE)
            val extractAlbum = mutationApiWithPath<ServerResponse<MediaInfo>, Unit, Path>("medias/{id}/extract-album")
        }

        val upload = mutationApiWithQuery<ServerResponse<MediaInfo>, Unit, ObjectTuple>("medias/upload")

        @Serializable
        class MediaSearchQuery(
            val name: String,
            val objectId: PrimaryKey,
            val objectType: ObjectType,
        )

        val getByName = safeApiWithQuery<MediaInfo, MediaSearchQuery>("medias/get-by-name")
    }

    object Titles {
        val add = mutationApi<TitleInfo, NewTitle>("titles")
    }

    object Accounts {
        val signIn = mutationApi<UserInfo, SignInPack>("/accounts/sign_in")
        val signOut = mutationApi<Unit, Unit>("/accounts/sign_out")
        val signUp = mutationApi<UserInfo, SignUpPack>("/accounts/sign_up")
        val getData = safeApi<String>("/accounts/get_data")

        object AlternativeAccounts {
            @Serializable
            class AlternativeAccountQuery(
                val nextPageToken: String? = null,
                val size: Int = 10,
            ) : PageableQuery {
                override val pagination: PaginationQuery
                    get() = PaginationQuery(nextPageToken, size = size)
            }

            val get =
                safeApiWithQuery<ServerResponse<AlternativeAccountInfo>, AlternativeAccountQuery>(
                    "/accounts/alternative_accounts"
                )
            val add = mutationApi<AlternativeAccountInfo, Unit>("/accounts/alternative_accounts")
            val delete = mutationApi<Unit, Unit>("/accounts/alternative_accounts")
        }
    }
}

@Serializable
class Path(val id: PrimaryKey)

@Serializable
class TopicQuery(
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
        paginationQuery: PaginationQuery,
    ) : this(
        pinType,
        fillHasCommented,
        paginationQuery.prePageToken,
        paginationQuery.nextPageToken,
        paginationQuery.size
    )
}
