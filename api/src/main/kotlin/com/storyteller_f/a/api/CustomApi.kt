package com.storyteller_f.a.api

import com.storyteller_f.route4k.common.MutationMethodType
import com.storyteller_f.route4k.common.mutationApi
import com.storyteller_f.route4k.common.mutationApiWithPath
import com.storyteller_f.route4k.common.mutationApiWithQuery
import com.storyteller_f.route4k.common.safeApi
import com.storyteller_f.route4k.common.safeApiWithPath
import com.storyteller_f.route4k.common.safeApiWithQuery
import com.storyteller_f.route4k.common.safeApiWithQueryAndPath
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

const val DEFAULT_PAGE_SIZE = 10

interface PageableQuery {
    val size: Int
    val nextPageToken: String?
    val prePageToken: String?
}

@Serializable
class PaginationQuery(
    override val nextPageToken: String? = null,
    override val prePageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE
) : PageableQuery

@Serializable
class CommonPath(val id: PrimaryKey)

@Serializable
class NewSubscription(
    val objectId: PrimaryKey,
    val objectType: ObjectType
) {
    fun tuple() = ObjectTuple(objectId, objectType)
}

@Serializable
class NewCommunity(
    val name: String,
    val aid: String,
    val icon: PrimaryKey? = null,
    val memberPolicy: MemberPolicy = MemberPolicy.OPEN
)

@Serializable
class NewDevice(val endpointUrl: String)

@Serializable
class NewReaction(val emoji: String)

@Serializable
class DeleteReaction(val emoji: String)

@Serializable
class NewRoom(
    val name: String,
    val aid: String,
    val icon: PrimaryKey? = null,
    val communityId: PrimaryKey? = null
)

@Serializable
class NewTitle(
    val name: String,
    val type: TitleType,
    val receiver: PrimaryKey,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val description: String,
)

@Serializable
class NewTopic(val parentType: ObjectType, val parentId: PrimaryKey, val content: String) {
    val tuple = ObjectTuple(parentId, parentType)
}

@Serializable
class NewFavorite(val objectType: ObjectType, val objectId: PrimaryKey) {
    fun tuple(): ObjectTuple {
        return ObjectTuple(objectId, objectType)
    }
}

@Serializable
class NewUser(
    val nickname: String? = null,
    val aid: String? = null,
    val publicKey: String,
)

@Serializable
class SignUpBody(val publicKey: String, val signature: String)

@Serializable
class SignInBody(val address: String, val signature: String)

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
            override val nextPageToken: String? = null,
            override val prePageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            val fillHasCommented: Boolean? = null
        ) : PageableQuery

        val search = safeApiWithQuery<ServerResponse<TopicInfo>, TopicSearchQuery>("topics/search")

        @Serializable
        class RecommendQuery(
            val fillHasCommented: Boolean? = null,
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val recommend =
            safeApiWithQuery<ServerResponse<TopicInfo>, RecommendQuery>("topics/recommend")

        object Id {
            @Serializable
            class TopicIdQuery(val fillHasCommented: Boolean? = null)

            val get = safeApiWithQueryAndPath<TopicInfo, TopicIdQuery, CommonPath>("topics/{id}")

            object Topics {
                val get =
                    safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>("topics/{id}/topics")
            }

            object Reactions {
                @Serializable
                class ReactionQuery(
                    val fillHasReacted: Boolean? = null,
                    override val nextPageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    override val prePageToken: String? = null,
                ) : PageableQuery

                val get =
                    safeApiWithQueryAndPath<ServerResponse<ReactionInfo>, ReactionQuery, CommonPath>(
                        "topics/{id}/reactions"
                    )
                val add =
                    mutationApiWithPath<ReactionInfo, NewReaction, CommonPath>("topics/{id}/reactions")
                val delete =
                    mutationApiWithPath<ReactionInfo, DeleteReaction, CommonPath>(
                        "topics/{id}/reactions",
                        methodType = MutationMethodType.DELETE
                    )
            }

            val pin = mutationApiWithPath<TopicInfo, Unit, CommonPath>("topics/{id}/pin")
            val unpin =
                mutationApiWithPath<TopicInfo, Unit, CommonPath>(
                    "topics/{id}/pin",
                    methodType = MutationMethodType.DELETE
                )
            val createSnapshot =
                mutationApiWithPath<FileInfo, Unit, CommonPath>("topics/{id}/create-snapshot")
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
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val search =
            safeApiWithQuery<ServerResponse<CommunityInfo>, CommunitySearchQuery>("communities/search")

        object Aid {
            @Serializable
            class CommunityAidQuery(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeApiWithQuery<CommunityInfo, CommunityAidQuery>("communities/aid")
        }

        object Id {
            @Serializable
            class CommunityIdQuery(val fillJoinInfo: Boolean = false)

            val get =
                safeApiWithQueryAndPath<CommunityInfo, CommunityIdQuery, CommonPath>("communities/{id}")

            object Members {
                @Serializable
                class CommunityMemberQuery(
                    val word: String? = null,
                    override val nextPageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    override val prePageToken: String? = null,
                ) : PageableQuery

                val get =
                    safeApiWithQueryAndPath<ServerResponse<UserInfo>, CommunityMemberQuery, CommonPath>(
                        "communities/{id}/members"
                    )
                val join =
                    mutationApiWithPath<CommunityInfo, Unit, CommonPath>("communities/{id}/members")
                val leave =
                    mutationApiWithPath<CommunityInfo, Unit, CommonPath>(
                        "communities/{id}/members",
                        methodType = MutationMethodType.DELETE
                    )
            }

            object Topics {
                val get =
                    safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>(
                        "communities/{id}/topics"
                    )
            }

            val update =
                mutationApiWithPath<CommunityInfo, UpdateCommunityBody, CommonPath>("communities/{id}")
        }

        val add = mutationApi<CommunityInfo, NewCommunity>("communities")
    }

    object Rooms {
        @Serializable
        data class RoomSearchQuery(
            val joinStatus: JoinStatusSearch? = null,
            val word: String? = null,
            val community: PrimaryKey? = null,
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val search = safeApiWithQuery<ServerResponse<RoomInfo>, RoomSearchQuery>("rooms/search")

        object Id {
            @Serializable
            class RoomIdQuery(val fillJoinInfo: Boolean = false)

            val get = safeApiWithQueryAndPath<RoomInfo, RoomIdQuery, CommonPath>("rooms/{id}")

            object Members {
                @Serializable
                class MemberQuery(
                    val word: String? = null,
                    override val nextPageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    override val prePageToken: String? = null
                ) :
                    PageableQuery

                val get =
                    safeApiWithQueryAndPath<ServerResponse<UserInfo>, MemberQuery, CommonPath>("rooms/{id}/members")
                val join =
                    mutationApiWithPath<RoomInfo, Unit, CommonPath>(
                        "rooms/{id}/members",
                        methodType = MutationMethodType.POST
                    )
                val leave =
                    mutationApiWithPath<RoomInfo, Unit, CommonPath>(
                        "rooms/{id}/members",
                        methodType = MutationMethodType.DELETE
                    )
                val publicKeys =
                    safeApiWithQueryAndPath<ServerResponse<UserPubKeyInfo>, PaginationQuery, CommonPath>(
                        "rooms/{id}/public-keys"
                    )
            }

            object Topics {
                val get =
                    safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>("rooms/{id}/topics")
            }

            val update =
                mutationApiWithPath<RoomInfo, UpdateRoomBody, CommonPath>("rooms/{id}/update")
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
            val get = safeApiWithPath<UserInfo, CommonPath>("users/{id}")

            object Topics {
                val get =
                    safeApiWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>("users/{id}/topics")
            }

            object Titles {
                @Serializable
                class TitleQuery(
                    val searchType: TitleSearchType,
                    val type: TitleType? = null,
                    val scopeId: PrimaryKey? = null,
                    val status: TitleStatus? = null,
                    override val nextPageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    override val prePageToken: String? = null,
                ) : PageableQuery

                val get =
                    safeApiWithQueryAndPath<ServerResponse<TitleInfo>, TitleQuery, CommonPath>("users/{id}/titles")
            }
        }

        @Serializable
        class UserSearchQuery(
            val word: String? = null,
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val search = safeApiWithQuery<ServerResponse<UserInfo>, UserSearchQuery>("users/search")

        val update = mutationApi<UserInfo, UpdateUserBody>("users/update")
        val overview = safeApi<UserOverview>("users/overview")

        object Read {
            val add = mutationApi<Unit, UpdateUserRead>("users/read")
        }

        object Devices {
            val add = mutationApi<Unit, NewDevice>("users/devices")
        }
    }

    object Files {
        @Serializable
        class FileQuery(
            val objectId: PrimaryKey? = null,
            val objectType: ObjectType? = null,
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val get = safeApiWithQuery<ServerResponse<FileInfo>, FileQuery>("files")

        object Id {
            val copy =
                mutationApiWithPath<ServerResponse<FileInfo>, Unit, CommonPath>("files/{id}/copy")
            val get = safeApiWithPath<FileInfo, CommonPath>("files/{id}")
            val extractAlbum =
                mutationApiWithPath<ServerResponse<FileInfo>, Unit, CommonPath>("files/{id}/extract-album")
        }

        val upload =
            mutationApiWithQuery<ServerResponse<FileInfo>, Unit, ObjectTuple>("files/upload")

        @Serializable
        class MediaSearchQuery(
            val name: String,
            val objectId: PrimaryKey,
            val objectType: ObjectType,
        )

        val getByName = safeApiWithQuery<FileInfo, MediaSearchQuery>("files/get-by-name")
    }

    object Titles {
        val add = mutationApi<TitleInfo, NewTitle>("titles")
    }

    object Accounts {
        val signIn = mutationApi<UserInfo, SignInBody>("/accounts/sign-in")
        val signOut = mutationApi<Unit, Unit>("/accounts/sign-out")
        val signUp = mutationApi<UserInfo, SignUpBody>("/accounts/sign-up")
        val getData = safeApi<String>("/accounts/get-data")

        object ChildAccounts {
            @Serializable
            class ChildAccountQuery(
                override val nextPageToken: String? = null,
                override val size: Int = DEFAULT_PAGE_SIZE,
                override val prePageToken: String? = null,
            ) : PageableQuery

            val get =
                safeApiWithQuery<ServerResponse<ChildAccountInfo>, ChildAccountQuery>(
                    "/accounts/child-accounts"
                )
            val add = mutationApi<ChildAccountInfo, Unit>("/accounts/child-accounts")
        }
    }

    object Subscriptions {
        val add = mutationApi<UserSubscriptionInfo, NewSubscription>("/subscriptions")
        val delete = mutationApiWithPath<Unit, Unit, CommonPath>("/subscriptions/{id}")
        val get =
            safeApiWithQuery<ServerResponse<UserSubscriptionInfo>, PaginationQuery>("/subscriptions")
    }

    object Favorites {
        val add = mutationApi<UserFavoriteInfo, NewFavorite>("/favorites")
        val delete = mutationApiWithPath<Unit, Unit, CommonPath>("/favorites/{id}")
        val get = safeApiWithQuery<ServerResponse<UserFavoriteInfo>, PaginationQuery>("/favorites")
    }
}

object AdminApi {
    object Users {
        val get = safeApiWithQuery<ServerResponse<UserInfo>, PaginationQuery>("/admin/users")
        val add = mutationApi<UserInfo, NewUser>("/admin/users")
    }

    val signIn = mutationApi<PanelAccountInfo, SignInBody>("/admin/sign-in")
    val signOut = mutationApi<Unit, Unit>("/admin/sign-out")
    val signUp = mutationApi<PanelAccountInfo, SignUpBody>("/admin/sign-up")
    val getData = safeApi<String>("/admin/get-data")
    val overview = safeApi<PanelOverview>("/admin/overview")
}

@Serializable
class TopicQuery(
    val pinType: TopicPinSearch? = null,
    val fillHasCommented: Boolean? = null,
    override val prePageToken: String? = null,
    override val nextPageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE,
) : PageableQuery {
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
