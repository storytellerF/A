package com.storyteller_f.a.api

import com.storyteller_f.endpoint4k.common.MutationMethodType
import com.storyteller_f.endpoint4k.common.mutationEndpoint
import com.storyteller_f.endpoint4k.common.mutationEndpointWithPath
import com.storyteller_f.endpoint4k.common.mutationEndpointWithQuery
import com.storyteller_f.endpoint4k.common.mutationEndpointWithQueryAndPath
import com.storyteller_f.endpoint4k.common.safeEndpoint
import com.storyteller_f.endpoint4k.common.safeEndpointWithPath
import com.storyteller_f.endpoint4k.common.safeEndpointWithQuery
import com.storyteller_f.endpoint4k.common.safeEndpointWithQueryAndPath
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.FileRefInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.obj.UpdateUserStatusBody
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UploadRecordStatus
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
class SearchQuery(
    val word: String,
    override val nextPageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE,
    override val prePageToken: String? = null,
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
    val algoType: AlgoType = AlgoType.P256
)

@Serializable
class SignUpBody(
    val publicKey: String,
    val signature: String,
)

@Serializable
class SignInBody(val address: String, val signature: String)

object CustomApi {
    object Topics {
        object Aid {
            @Serializable
            class TopicAidQuery(val aid: String, val fillHasCommented: Boolean? = null)

            val get = safeEndpointWithQuery<TopicInfo, TopicAidQuery>("topics/aid")
        }

        @Serializable
        class TopicSearchQuery(
            val word: String,
            val parentId: PrimaryKey? = null,
            val parentType: ObjectType? = null,
            override val nextPageToken: String? = null,
            override val prePageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            val fillHasCommented: Boolean? = null
        ) : PageableQuery

        @Serializable
        class RecommendQuery(
            val fillHasCommented: Boolean? = null,
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val recommend = safeEndpointWithQuery<ServerResponse<TopicInfo>, RecommendQuery>("topics/recommend")

        // 用户主题搜索端点
        object Users {
            object Id {
                @Serializable
                class UserTopicSearchQuery(
                    val word: String,
                    override val nextPageToken: String? = null,
                    override val prePageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    val fillHasCommented: Boolean? = null
                ) : PageableQuery

                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, UserTopicSearchQuery, CommonPath>(
                        "users/{id}/topics/search"
                    )
            }
        }

        // 房间主题搜索端点
        object Rooms {
            object Id {
                @Serializable
                class RoomTopicSearchQuery(
                    val word: String,
                    override val nextPageToken: String? = null,
                    override val prePageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    val fillHasCommented: Boolean? = null
                ) : PageableQuery

                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, RoomTopicSearchQuery, CommonPath>(
                        "rooms/{id}/topics/search"
                    )
            }
        }

        // 社区主题搜索端点
        object Communities {
            object Id {
                @Serializable
                class CommunityTopicSearchQuery(
                    val word: String,
                    override val nextPageToken: String? = null,
                    override val prePageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    val fillHasCommented: Boolean? = null
                ) : PageableQuery

                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, CommunityTopicSearchQuery, CommonPath>(
                        "communities/{id}/topics/search"
                    )
            }
        }

        object Id {
            @Serializable
            class TopicIdQuery(val fillHasCommented: Boolean? = null)

            val get = safeEndpointWithQueryAndPath<TopicInfo, TopicIdQuery, CommonPath>("topics/{id}")

            object Topics {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>(
                        "topics/{id}/topics"
                    )
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
                    safeEndpointWithQueryAndPath<ServerResponse<ReactionInfo>, ReactionQuery, CommonPath>(
                        "topics/{id}/reactions"
                    )
                val add = mutationEndpointWithPath<ReactionInfo, NewReaction, CommonPath>("topics/{id}/reactions")
                val delete =
                    mutationEndpointWithPath<ReactionInfo, DeleteReaction, CommonPath>(
                        "topics/{id}/reactions",
                        methodType = MutationMethodType.DELETE
                    )
            }

            object Favorite {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("topics/{id}/favorite")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "topics/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                )
            }

            object Subscription {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("topics/{id}/subscription")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "topics/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                )
            }

            val pin = mutationEndpointWithPath<TopicInfo, Unit, CommonPath>("topics/{id}/pin")
            val unpin =
                mutationEndpointWithPath<TopicInfo, Unit, CommonPath>(
                    "topics/{id}/pin",
                    methodType = MutationMethodType.DELETE
                )
            val createSnapshot = mutationEndpointWithPath<FileInfo, Unit, CommonPath>("topics/{id}/create-snapshot")
        }

        val add = mutationEndpoint<TopicInfo, NewTopic>("topics")
    }

    object Root {
        val get = safeEndpoint<String>("/")
    }

    object Communities {
        @Serializable
        data class CommunitySearchQuery(
            val joinStatus: JoinStatusSearch? = null,
            val word: String,
            val target: PrimaryKey? = null,
            val hasPoster: PosterSearch? = null,
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val search = safeEndpointWithQuery<ServerResponse<CommunityInfo>, CommunitySearchQuery>("communities/search")

        object Aid {
            @Serializable
            class CommunityAidQuery(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeEndpointWithQuery<CommunityInfo, CommunityAidQuery>("communities/aid")
        }

        object Id {
            @Serializable
            class CommunityIdQuery(val fillJoinInfo: Boolean = false)

            val get = safeEndpointWithQueryAndPath<CommunityInfo, CommunityIdQuery, CommonPath>("communities/{id}")

            object Members {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<MemberInfo>, PaginationQuery, CommonPath>(
                        "communities/{id}/members"
                    )
                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<MemberInfo>, SearchQuery, CommonPath>(
                        "communities/{id}/members/search"
                    )
                val join = mutationEndpointWithPath<CommunityInfo, Unit, CommonPath>("communities/{id}/members")
                val leave =
                    mutationEndpointWithPath<CommunityInfo, Unit, CommonPath>(
                        "communities/{id}/members",
                        methodType = MutationMethodType.DELETE
                    )
            }

            object Topics {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>(
                        "communities/{id}/topics"
                    )
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<FileInfo>, PaginationQuery, CommonPath>(
                        "communities/{id}/files"
                    )

                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<FileInfo>, SearchQuery, CommonPath>(
                        "communities/{id}/files/search"
                    )
            }

            object Rooms {
                @Serializable
                data class CommunityRoomQuery(
                    override val nextPageToken: String? = null,
                    override val prePageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    val joinStatus: JoinStatusSearch? = null
                ) : PageableQuery

                @Serializable
                data class CommunityRoomSearchQuery(
                    val word: String,
                    val joinStatus: JoinStatusSearch? = null,
                    override val nextPageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    override val prePageToken: String? = null,
                ) : PageableQuery

                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<RoomInfo>, CommunityRoomQuery, CommonPath>(
                        "communities/{id}/rooms"
                    )
                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<RoomInfo>, CommunityRoomSearchQuery, CommonPath>(
                        "communities/{id}/rooms/search"
                    )
            }

            object Favorite {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("communities/{id}/favorite")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "communities/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                )
            }

            object Subscription {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("communities/{id}/subscription")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "communities/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                )
            }

            val update = mutationEndpointWithPath<CommunityInfo, UpdateCommunityBody, CommonPath>("communities/{id}")
        }

        val add = mutationEndpoint<CommunityInfo, NewCommunity>("communities")
    }

    object Rooms {

        object Id {
            @Serializable
            class RoomIdQuery(val fillJoinInfo: Boolean = false)

            val get = safeEndpointWithQueryAndPath<RoomInfo, RoomIdQuery, CommonPath>("rooms/{id}")

            object Members {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<MemberInfo>, PaginationQuery, CommonPath>(
                        "rooms/{id}/members"
                    )
                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<MemberInfo>, SearchQuery, CommonPath>(
                        "rooms/{id}/members/search"
                    )
                val join =
                    mutationEndpointWithPath<RoomInfo, Unit, CommonPath>(
                        "rooms/{id}/members",
                        methodType = MutationMethodType.POST
                    )
                val leave =
                    mutationEndpointWithPath<RoomInfo, Unit, CommonPath>(
                        "rooms/{id}/members",
                        methodType = MutationMethodType.DELETE
                    )
                val publicKeys =
                    safeEndpointWithQueryAndPath<ServerResponse<UserPubKeyInfo>, PaginationQuery, CommonPath>(
                        "rooms/{id}/public-keys"
                    )
            }

            object Topics {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>("rooms/{id}/topics")
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<FileInfo>, PaginationQuery, CommonPath>(
                        "rooms/{id}/files"
                    )

                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<FileInfo>, SearchQuery, CommonPath>(
                        "rooms/{id}/files/search"
                    )
            }

            object Favorite {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("rooms/{id}/favorite")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "rooms/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                )
            }

            object Subscription {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("rooms/{id}/subscription")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "rooms/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                )
            }

            val update = mutationEndpointWithPath<RoomInfo, UpdateRoomBody, CommonPath>("rooms/{id}/update")
        }

        object Aid {
            @Serializable
            class RoomAidQuery(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeEndpointWithQuery<RoomInfo, RoomAidQuery>("rooms/aid")
        }

        val add = mutationEndpoint<RoomInfo, NewRoom>("rooms")
    }

    object Users {
        object Aid {
            @Serializable
            class UserAidQuery(val aid: String)

            val get = safeEndpointWithQuery<UserInfo, UserAidQuery>("users/aid")
        }

        object Id {
            val get = safeEndpointWithPath<UserInfo, CommonPath>("users/{id}")

            object Topics {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>("users/{id}/topics")
            }

            object Communities {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<CommunityInfo>, PaginationQuery, CommonPath>(
                        "users/{id}/communities"
                    )
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<FileInfo>, PaginationQuery, CommonPath>(
                        "users/{id}/files"
                    )

                val search =
                    safeEndpointWithQueryAndPath<ServerResponse<FileInfo>, SearchQuery, CommonPath>(
                        "users/{id}/files/search"
                    )
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
                    safeEndpointWithQueryAndPath<ServerResponse<TitleInfo>, TitleQuery, CommonPath>("users/{id}/titles")
            }

            object Favorites {
                val get = safeEndpointWithQueryAndPath<ServerResponse<UserFavoriteInfo>, PaginationQuery, CommonPath>(
                    "users/{id}/favorites"
                )
            }

            object Subscriptions {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<UserSubscriptionInfo>, PaginationQuery, CommonPath>(
                        "users/{id}/subscriptions"
                    )
            }

            object Favorite {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("users/{id}/favorite")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "users/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                )
            }

            object Subscription {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("users/{id}/subscription")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "users/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                )
            }
        }

        val search = safeEndpointWithQuery<ServerResponse<UserInfo>, SearchQuery>("users/search")

        val update = mutationEndpoint<UserInfo, UpdateUserBody>("users/update")
        val overview = safeEndpoint<UserOverview>("users/overview")

        object Read {
            val add = mutationEndpoint<Unit, UpdateUserRead>("users/read")
        }

        object Devices {
            val add = mutationEndpoint<Unit, NewDevice>("users/devices")
        }

        object ReactionRecords {
            val get = safeEndpointWithQuery<ServerResponse<ReactionRecordInfo>, PaginationQuery>(
                "users/reaction-records"
            )
        }

        object Comments {
            val get = safeEndpointWithQuery<ServerResponse<TopicInfo>, PaginationQuery>("users/comments")
        }

        object JoinedRooms {
            @Serializable
            data class UserRoomsSearchQuery(
                val word: String,
                override val nextPageToken: String? = null,
                override val size: Int = DEFAULT_PAGE_SIZE,
                override val prePageToken: String? = null,
            ) : PageableQuery

            val get = safeEndpointWithQuery<ServerResponse<RoomInfo>, PaginationQuery>("users/joined-rooms")

            val search =
                safeEndpointWithQuery<ServerResponse<RoomInfo>, UserRoomsSearchQuery>("users/joined-rooms/search")
        }

        object JoinedCommunities {
            @Serializable
            data class UserCommunitiesSearchQuery(
                val word: String,
                override val nextPageToken: String? = null,
                override val size: Int = DEFAULT_PAGE_SIZE,
                override val prePageToken: String? = null,
            ) : PageableQuery

            val get = safeEndpointWithQuery<ServerResponse<CommunityInfo>, PaginationQuery>("users/joined-communities")
            val search =
                safeEndpointWithQuery<ServerResponse<CommunityInfo>, UserCommunitiesSearchQuery>(
                    "users/joined-communities/search"
                )
        }
    }

    object Files {

        object Id {
            val copy = mutationEndpointWithPath<ServerResponse<FileInfo>, Unit, CommonPath>("files/{id}/copy")
            val get = safeEndpointWithPath<FileInfo, CommonPath>("files/{id}")
            val extractAlbum =
                mutationEndpointWithPath<ServerResponse<FileInfo>, Unit, CommonPath>("files/{id}/extract-album")

            object Refs {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<FileRefInfo>, PaginationQuery, CommonPath>(
                        "files/{id}/refs"
                    )
            }

            object Favorite {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("files/{id}/favorite")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "files/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                )
            }

            object Subscription {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("files/{id}/subscription")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "files/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                )
            }
        }

        val upload = mutationEndpointWithQuery<ServerResponse<FileInfo>, Unit, ObjectTuple>("files/upload")

        @Serializable
        class MediaSearchQuery(
            val name: String,
            val objectId: PrimaryKey,
            val objectType: ObjectType,
        )

        val getByName = safeEndpointWithQuery<FileInfo, MediaSearchQuery>("files/get-by-name")

        @Serializable
        class QuotaQuery(
            val objectId: PrimaryKey,
            val objectType: ObjectType,
            val quotaType: QuotaType = QuotaType.FILE
        )

        val quota = safeEndpointWithQuery<QuotaInfo, QuotaQuery>("files/quota")

        object Chunks {
            @Serializable
            class InitBody(
                val objectId: PrimaryKey,
                val objectType: ObjectType,
                val name: String,
                val size: Long,
                val contentType: String,
                val chunkSize: Long
            )

            @Serializable
            class InitResponse(
                val recordId: PrimaryKey,
                val chunkSize: Long
            )

            @Serializable
            class UploadQuery(val hash: String)

            val init = mutationEndpoint<InitResponse, InitBody>("files/chunk/init")

            @Serializable
            class UploadPath(
                val id: PrimaryKey,
                val index: Int
            )

            val upload =
                mutationEndpointWithQueryAndPath<Unit, Unit, UploadQuery, UploadPath>("files/chunk/{id}/{index}/upload")

            val complete = mutationEndpointWithPath<FileInfo, Unit, CommonPath>("files/chunk/{id}/complete")
            val abort = mutationEndpointWithPath<Unit, Unit, CommonPath>("files/chunk/{id}/abort")

            @Serializable
            class StatusResponse(
                val uploaded: List<Int>,
                val chunkSize: Long,
                val size: Long,
                val id: PrimaryKey,
                val status: UploadRecordStatus
            )

            val status = safeEndpointWithPath<StatusResponse, CommonPath>("files/chunk/{id}/status")
        }
    }

    object Titles {
        object Id {
            object Favorite {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("titles/{id}/favorite")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "titles/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                )
            }

            object Subscription {
                val add = mutationEndpointWithPath<Unit, Unit, CommonPath>("titles/{id}/subscription")
                val delete = mutationEndpointWithPath<Unit, Unit, CommonPath>(
                    "titles/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                )
            }
        }

        val add = mutationEndpoint<TitleInfo, NewTitle>("titles")
    }

    object Accounts {
        val signIn = mutationEndpoint<UserInfo, SignInBody>("/accounts/sign-in")
        val signOut = mutationEndpoint<Unit, Unit>("/accounts/sign-out")
        val signUp = mutationEndpoint<UserInfo, SignUpBody>("/accounts/sign-up")
        val getData = safeEndpoint<String>("/accounts/get-data")

        object ChildAccounts {
            @Serializable
            class ChildAccountQuery(
                override val nextPageToken: String? = null,
                override val size: Int = DEFAULT_PAGE_SIZE,
                override val prePageToken: String? = null,
            ) : PageableQuery

            @Serializable
            class AddChildAccountRequest(
                val encryptedPrivateKey: String,
                val encryptedAesKey: String,
                val derPublicKey: String,
                val algoType: AlgoType = AlgoType.P256
            )

            val get =
                safeEndpointWithQuery<ServerResponse<ChildAccountInfo>, ChildAccountQuery>("/accounts/child-accounts")
            val add = mutationEndpoint<ChildAccountInfo, AddChildAccountRequest>("/accounts/child-accounts")
        }
    }
}

object AdminApi {
    object Users {
        val get = safeEndpointWithQuery<ServerResponse<UserInfo>, PaginationQuery>("/admin/users")
        val add = mutationEndpoint<UserInfo, NewUser>("/admin/users")

        object Id {
            val get = safeEndpointWithPath<UserInfo, CommonPath>("/admin/users/{id}")

            object Overview {
                val get = safeEndpointWithPath<UserOverview, CommonPath>("/admin/users/{id}/overview")
            }

            object Status {
                val update = mutationEndpointWithPath<
                    Unit,
                    UpdateUserStatusBody,
                    CommonPath>(
                    "/admin/users/{id}/status"
                )
            }

            object Communities {
                val get = safeEndpointWithQueryAndPath<
                    ServerResponse<CommunityInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/communities"
                )
            }

            object Rooms {
                val get = safeEndpointWithQueryAndPath<
                    ServerResponse<RoomInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/rooms"
                )
            }

            object Titles {
                val get = safeEndpointWithQueryAndPath<
                    ServerResponse<TitleInfo>,
                    CustomApi.Users.Id.Titles.TitleQuery,
                    CommonPath>(
                    "/admin/users/{id}/titles"
                )
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<FileInfo>, PaginationQuery, CommonPath>(
                        "/admin/users/{id}/files"
                    )
            }

            object Logs {
                val get = safeEndpointWithQueryAndPath<ServerResponse<UserLogInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/logs"
                )
            }

            object UploadRecords {
                val get = safeEndpointWithQueryAndPath<ServerResponse<com.storyteller_f.shared.model.UploadRecordInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/upload-records"
                )
            }

            object Reactions {
                val get = safeEndpointWithQueryAndPath<ServerResponse<ReactionRecordInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/reactions"
                )
            }

            object Comments {
                val get = safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/comments"
                )
            }

            object Favorites {
                val get = safeEndpointWithQueryAndPath<ServerResponse<UserFavoriteInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/favorites"
                )
            }

            object Subscriptions {
                val get = safeEndpointWithQueryAndPath<ServerResponse<UserSubscriptionInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/users/{id}/subscriptions"
                )
            }
        }
    }

    val signIn = mutationEndpoint<PanelAccountInfo, SignInBody>("/admin/sign-in")
    val signOut = mutationEndpoint<Unit, Unit>("/admin/sign-out")
    val signUp = mutationEndpoint<PanelAccountInfo, SignUpBody>("/admin/sign-up")
    val getData = safeEndpoint<String>("/admin/get-data")
    val overview = safeEndpoint<PanelOverview>("/admin/overview")

    object Communities {
        val get = safeEndpointWithQuery<ServerResponse<CommunityInfo>, PaginationQuery>("/admin/communities")

        object Id {
            val get = safeEndpointWithPath<CommunityInfo, CommonPath>("/admin/communities/{id}")

            object Members {
                val get = safeEndpointWithQueryAndPath<
                    ServerResponse<MemberInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/communities/{id}/members"
                )
            }
        }
    }

    object Rooms {
        val getPublic = safeEndpointWithQuery<ServerResponse<RoomInfo>, PaginationQuery>("/admin/rooms/public")
        val getPrivate = safeEndpointWithQuery<ServerResponse<RoomInfo>, PaginationQuery>("/admin/rooms/private")

        object Id {
            val get = safeEndpointWithPath<RoomInfo, CommonPath>("/admin/rooms/{id}")

            object Members {
                val get = safeEndpointWithQueryAndPath<
                    ServerResponse<MemberInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/rooms/{id}/members"
                )
            }

            object Files {
                val get = safeEndpointWithQueryAndPath<
                    ServerResponse<FileInfo>,
                    PaginationQuery,
                    CommonPath>(
                    "/admin/rooms/{id}/files"
                )
            }
        }
    }

    object Topics {
        val get = safeEndpointWithQuery<ServerResponse<TopicInfo>, PaginationQuery>("/admin/topics")

        object Id {
            val get = safeEndpointWithPath<TopicInfo, CommonPath>("/admin/topics/{id}")

            object Topics {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<TopicInfo>, TopicQuery, CommonPath>(
                        "/admin/topics/{id}/topics"
                    )
            }
        }
    }

    object Titles {
        val get = safeEndpointWithQuery<ServerResponse<TitleInfo>, PaginationQuery>("/admin/titles")

        object Id {
            val get = safeEndpointWithPath<TitleInfo, CommonPath>("/admin/titles/{id}")
        }
    }

    object Files {
        val get = safeEndpointWithQuery<ServerResponse<FileInfo>, PaginationQuery>("/admin/files")

        val search = safeEndpointWithQuery<ServerResponse<FileInfo>, SearchQuery>("/admin/files/search")

        object Id {
            val get = safeEndpointWithPath<FileInfo, CommonPath>("/admin/files/{id}")

            object Refs {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<FileRefInfo>, PaginationQuery, CommonPath>(
                        "/admin/files/{id}/refs"
                    )
            }
        }
    }
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
