package com.storyteller_f.a.api

import com.storyteller_f.endpoint4k.common.MutationMethodType
import com.storyteller_f.endpoint4k.common.body
import com.storyteller_f.endpoint4k.common.mutationEndpointBuilder
import com.storyteller_f.endpoint4k.common.mutationEndpointWithPathBuilder
import com.storyteller_f.endpoint4k.common.mutationEndpointWithQueryAndPathBuilder
import com.storyteller_f.endpoint4k.common.mutationEndpointWithQueryBuilder
import com.storyteller_f.endpoint4k.common.path
import com.storyteller_f.endpoint4k.common.query
import com.storyteller_f.endpoint4k.common.resp
import com.storyteller_f.endpoint4k.common.safeEndpointBuilder
import com.storyteller_f.endpoint4k.common.safeEndpointWithPathBuilder
import com.storyteller_f.endpoint4k.common.safeEndpointWithQueryAndPathBuilder
import com.storyteller_f.endpoint4k.common.safeEndpointWithQueryBuilder
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TitleWorkStatus
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.ListResponse
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.CustomImmutableList
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UploadRecordStatus
import kotlinx.serialization.Serializable

@Serializable
data class ReactionInfoListResponse(
    override val data: CustomImmutableList<ReactionInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<ReactionInfo>

@Serializable
data class UserPubKeyInfoListResponse(
    override val data: CustomImmutableList<UserPubKeyInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<UserPubKeyInfo>

@Serializable
data class ChildAccountInfoListResponse(
    override val data: CustomImmutableList<ChildAccountInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<ChildAccountInfo>

const val DEFAULT_PAGE_SIZE = 10
const val MAX_PAGE_SIZE = 100

object CustomApi {
    object Topics {
        object Aid {
            @Serializable
            class TopicAidQuery(val aid: String, val fillHasCommented: Boolean? = null)

            val get = safeEndpointWithQueryBuilder("topics/aid") {
                resp(TopicInfo::class)
                query(TopicAidQuery::class)
            }
        }

        @Serializable
        class RecommendQuery(
            val fillHasCommented: Boolean? = null,
            override val nextPageToken: String? = null,
            override val size: Int = DEFAULT_PAGE_SIZE,
            override val prePageToken: String? = null,
        ) : PageableQuery

        val recommend = safeEndpointWithQueryBuilder("topics/recommend") {
            resp(TopicInfoListResponse::class)
            query(RecommendQuery::class)
        }

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
                    safeEndpointWithQueryAndPathBuilder("users/{id}/topics/search") {
                        resp(TopicInfoListResponse::class)
                        query(UserTopicSearchQuery::class)
                        path(CommonPath::class)
                    }
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
                    safeEndpointWithQueryAndPathBuilder("rooms/{id}/topics/search") {
                        resp(TopicInfoListResponse::class)
                        query(RoomTopicSearchQuery::class)
                        path(CommonPath::class)
                    }
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
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/topics/search") {
                        resp(TopicInfoListResponse::class)
                        query(CommunityTopicSearchQuery::class)
                        path(CommonPath::class)
                    }
            }
        }

        object Id {
            @Serializable
            class TopicIdQuery(val fillHasCommented: Boolean? = null)

            val get = safeEndpointWithQueryAndPathBuilder("topics/{id}") {
                resp(TopicInfo::class)
                query(TopicIdQuery::class)
                path(CommonPath::class)
            }

            object Topics {
                val get =
                    safeEndpointWithQueryAndPathBuilder("topics/{id}/topics") {
                        resp(TopicInfoListResponse::class)
                        query(TopicQuery::class)
                        path(CommonPath::class)
                    }
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
                    safeEndpointWithQueryAndPathBuilder("topics/{id}/reactions") {
                        resp(ReactionInfoListResponse::class)
                        query(ReactionQuery::class)
                        path(CommonPath::class)
                    }
                val add = mutationEndpointWithPathBuilder("topics/{id}/reactions") {
                    resp(ReactionInfo::class)
                    body(NewReaction::class)
                    path(CommonPath::class)
                }
                val delete =
                    mutationEndpointWithPathBuilder("topics/{id}/reactions", methodType = MutationMethodType.DELETE) {
                        resp(ReactionInfo::class)
                        body(DeleteReaction::class)
                        path(CommonPath::class)
                    }
            }

            object Favorite {
                val add = mutationEndpointWithPathBuilder("topics/{id}/favorite") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "topics/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            object Subscription {
                val add = mutationEndpointWithPathBuilder("topics/{id}/subscription") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "topics/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            val pin = mutationEndpointWithPathBuilder("topics/{id}/pin") {
                resp(TopicInfo::class)
                body(Unit::class)
                path(CommonPath::class)
            }
            val unpin =
                mutationEndpointWithPathBuilder("topics/{id}/pin", methodType = MutationMethodType.DELETE) {
                    resp(TopicInfo::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            val createSnapshot = mutationEndpointWithPathBuilder("topics/{id}/create-snapshot") {
                resp(FileInfo::class)
                body(Unit::class)
                path(CommonPath::class)
            }
        }

        val add = mutationEndpointBuilder("topics") {
            resp(TopicInfo::class)
            body(NewTopic::class)
        }
    }

    object Root {
        val get = safeEndpointBuilder("/") {
            resp(String::class)
        }
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

        val search = safeEndpointWithQueryBuilder("communities/search") {
            resp(CommunityInfoListResponse::class)
            query(CommunitySearchQuery::class)
        }

        object Aid {
            @Serializable
            class CommunityAidQuery(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeEndpointWithQueryBuilder("communities/aid") {
                resp(CommunityInfo::class)
                query(CommunityAidQuery::class)
            }
        }

        object Id {
            @Serializable
            class CommunityIdQuery(val fillJoinInfo: Boolean = false)

            val get = safeEndpointWithQueryAndPathBuilder("communities/{id}") {
                resp(CommunityInfo::class)
                query(CommunityIdQuery::class)
                path(CommonPath::class)
            }

            object Members {
                val get =
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/members") {
                        resp(MemberInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }
                val search =
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/members/search") {
                        resp(MemberInfoListResponse::class)
                        query(SearchQuery::class)
                        path(CommonPath::class)
                    }
                val join = mutationEndpointWithPathBuilder("communities/{id}/members") {
                    resp(CommunityInfo::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val leave =
                    mutationEndpointWithPathBuilder(
                        "communities/{id}/members",
                        methodType = MutationMethodType.DELETE
                    ) {
                        resp(CommunityInfo::class)
                        body(Unit::class)
                        path(CommonPath::class)
                    }
            }

            object Topics {
                val get =
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/topics") {
                        resp(TopicInfoListResponse::class)
                        query(TopicQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/files") {
                        resp(FileInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }

                val search =
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/files/search") {
                        resp(FileInfoListResponse::class)
                        query(SearchQuery::class)
                        path(CommonPath::class)
                    }
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
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/rooms") {
                        resp(RoomInfoListResponse::class)
                        query(CommunityRoomQuery::class)
                        path(CommonPath::class)
                    }
                val search =
                    safeEndpointWithQueryAndPathBuilder("communities/{id}/rooms/search") {
                        resp(RoomInfoListResponse::class)
                        query(CommunityRoomSearchQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Favorite {
                val add = mutationEndpointWithPathBuilder("communities/{id}/favorite") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "communities/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            object Subscription {
                val add = mutationEndpointWithPathBuilder("communities/{id}/subscription") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "communities/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            val update = mutationEndpointWithPathBuilder("communities/{id}") {
                resp(CommunityInfo::class)
                body(UpdateCommunityBody::class)
                path(CommonPath::class)
            }
        }

        val add = mutationEndpointBuilder("communities") {
            resp(CommunityInfo::class)
            body(NewCommunity::class)
        }
    }

    object Rooms {

        object Id {
            @Serializable
            class RoomIdQuery(val fillJoinInfo: Boolean = false)

            val get = safeEndpointWithQueryAndPathBuilder("rooms/{id}") {
                resp(RoomInfo::class)
                query(RoomIdQuery::class)
                path(CommonPath::class)
            }

            object Members {
                val get =
                    safeEndpointWithQueryAndPathBuilder("rooms/{id}/members") {
                        resp(MemberInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }
                val search =
                    safeEndpointWithQueryAndPathBuilder("rooms/{id}/members/search") {
                        resp(MemberInfoListResponse::class)
                        query(SearchQuery::class)
                        path(CommonPath::class)
                    }
                val join =
                    mutationEndpointWithPathBuilder("rooms/{id}/members", methodType = MutationMethodType.POST) {
                        resp(RoomInfo::class)
                        body(Unit::class)
                        path(CommonPath::class)
                    }
                val leave =
                    mutationEndpointWithPathBuilder("rooms/{id}/members", methodType = MutationMethodType.DELETE) {
                        resp(RoomInfo::class)
                        body(Unit::class)
                        path(CommonPath::class)
                    }
                val publicKeys =
                    safeEndpointWithQueryAndPathBuilder("rooms/{id}/public-keys") {
                        resp(UserPubKeyInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Topics {
                val get =
                    safeEndpointWithQueryAndPathBuilder("rooms/{id}/topics") {
                        resp(TopicInfoListResponse::class)
                        query(TopicQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPathBuilder("rooms/{id}/files") {
                        resp(FileInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }

                val search =
                    safeEndpointWithQueryAndPathBuilder("rooms/{id}/files/search") {
                        resp(FileInfoListResponse::class)
                        query(SearchQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Favorite {
                val add = mutationEndpointWithPathBuilder("rooms/{id}/favorite") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "rooms/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            object Subscription {
                val add = mutationEndpointWithPathBuilder("rooms/{id}/subscription") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "rooms/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            val update = mutationEndpointWithPathBuilder("rooms/{id}/update") {
                resp(RoomInfo::class)
                body(UpdateRoomBody::class)
                path(CommonPath::class)
            }
        }

        object Aid {
            @Serializable
            class RoomAidQuery(val aid: String, val fillJoinInfo: Boolean? = null)

            val get = safeEndpointWithQueryBuilder("rooms/aid") {
                resp(RoomInfo::class)
                query(RoomAidQuery::class)
            }
        }

        val add = mutationEndpointBuilder("rooms") {
            resp(RoomInfo::class)
            body(NewRoom::class)
        }
    }

    object Users {
        object Aid {
            @Serializable
            class UserAidQuery(val aid: String)

            val get = safeEndpointWithQueryBuilder("users/aid") {
                resp(UserInfo::class)
                query(UserAidQuery::class)
            }
        }

        object Id {
            val get = safeEndpointWithPathBuilder("users/{id}") {
                resp(UserInfo::class)
                path(CommonPath::class)
            }

            object Topics {
                val get =
                    safeEndpointWithQueryAndPathBuilder("users/{id}/topics") {
                        resp(TopicInfoListResponse::class)
                        query(TopicQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Communities {
                val get = safeEndpointWithQueryAndPathBuilder("users/{id}/communities") {
                    resp(CommunityInfoListResponse::class)
                    query(JoinedCommunities.UserCommunitiesQuery::class)
                    path(CommonPath::class)
                }
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPathBuilder("users/{id}/files") {
                        resp(FileInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }

                val search =
                    safeEndpointWithQueryAndPathBuilder("users/{id}/files/search") {
                        resp(FileInfoListResponse::class)
                        query(SearchQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Titles {
                @Serializable
                class TitleQuery(
                    val searchType: TitleSearchType,
                    val type: TitleType? = null,
                    val scopeId: PrimaryKey? = null,
                    val titleStatus: TitleWorkStatus? = null,
                    override val nextPageToken: String? = null,
                    override val size: Int = DEFAULT_PAGE_SIZE,
                    override val prePageToken: String? = null,
                ) : PageableQuery

                val get =
                    safeEndpointWithQueryAndPathBuilder("users/{id}/titles") {
                        resp(TitleInfoListResponse::class)
                        query(TitleQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Favorites {
                val get = safeEndpointWithQueryAndPathBuilder("users/{id}/favorites") {
                    resp(UserFavoriteInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object Subscriptions {
                val get =
                    safeEndpointWithQueryAndPathBuilder("users/{id}/subscriptions") {
                        resp(UserSubscriptionInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Favorite {
                val add = mutationEndpointWithPathBuilder("users/{id}/favorite") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "users/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            object Subscription {
                val add = mutationEndpointWithPathBuilder("users/{id}/subscription") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "users/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }
        }

        val search = safeEndpointWithQueryBuilder("users/search") {
            resp(UserInfoListResponse::class)
            query(SearchQuery::class)
        }

        val update = mutationEndpointBuilder("users/update") {
            resp(UserInfo::class)
            body(UpdateUserBody::class)
        }
        val overview = safeEndpointBuilder("users/overview") {
            resp(UserOverview::class)
        }

        object Read {
            val add = mutationEndpointBuilder("users/read") {
                resp(Unit::class)
                body(UpdateUserRead::class)
            }
        }

        object Devices {
            val add = mutationEndpointBuilder("users/devices") {
                resp(Unit::class)
                body(NewDevice::class)
            }
        }

        object ReactionRecords {
            val get = safeEndpointWithQueryBuilder("users/reaction-records") {
                resp(ReactionRecordInfoListResponse::class)
                query(PaginationQuery::class)
            }
        }

        object Comments {
            val get = safeEndpointWithQueryBuilder("users/comments") {
                resp(TopicInfoListResponse::class)
                query(PaginationQuery::class)
            }
        }

        object JoinedRooms {
            @Serializable
            data class UserRoomsSearchQuery(
                val word: String,
                override val nextPageToken: String? = null,
                override val size: Int = DEFAULT_PAGE_SIZE,
                override val prePageToken: String? = null,
            ) : PageableQuery

            val get = safeEndpointWithQueryBuilder("users/joined-rooms") {
                resp(RoomInfoListResponse::class)
                query(PaginationQuery::class)
            }

            val search =
                safeEndpointWithQueryBuilder("users/joined-rooms/search") {
                    resp(RoomInfoListResponse::class)
                    query(UserRoomsSearchQuery::class)
                }
        }

        object JoinedCommunities {
            @Serializable
            class UserCommunitiesQuery(
                val hasPoster: PosterSearch? = null,
                override val nextPageToken: String? = null,
                override val size: Int = DEFAULT_PAGE_SIZE,
                override val prePageToken: String? = null,
            ) : PageableQuery

            @Serializable
            data class UserCommunitiesSearchQuery(
                val word: String,
                override val nextPageToken: String? = null,
                override val size: Int = DEFAULT_PAGE_SIZE,
                override val prePageToken: String? = null,
            ) : PageableQuery

            val get = safeEndpointWithQueryBuilder("users/joined-communities") {
                resp(CommunityInfoListResponse::class)
                query(UserCommunitiesQuery::class)
            }
            val search =
                safeEndpointWithQueryBuilder("users/joined-communities/search") {
                    resp(CommunityInfoListResponse::class)
                    query(UserCommunitiesSearchQuery::class)
                }
        }
    }

    object Files {

        object Id {
            val copy = mutationEndpointWithPathBuilder("files/{id}/copy") {
                resp(FileInfo::class)
                body(Unit::class)
                path(CommonPath::class)
            }
            val get = safeEndpointWithPathBuilder("files/{id}") {
                resp(FileInfo::class)
                path(CommonPath::class)
            }
            val extractAlbum = mutationEndpointWithPathBuilder("files/{id}/extract-album") {
                resp(FileInfo::class)
                body(Unit::class)
                path(CommonPath::class)
            }

            object Refs {
                val get =
                    safeEndpointWithQueryAndPathBuilder("files/{id}/refs") {
                        resp(FileRefInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Favorite {
                val add = mutationEndpointWithPathBuilder("files/{id}/favorite") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "files/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            object Subscription {
                val add = mutationEndpointWithPathBuilder("files/{id}/subscription") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "files/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }
        }

        val upload = mutationEndpointWithQueryBuilder("files/upload") {
            resp(FileInfoListResponse::class)
            body(Unit::class)
            query(ObjectTuple::class)
        }

        @Serializable
        class MediaSearchQuery(
            val name: String,
            val objectId: PrimaryKey,
            val objectType: ObjectType,
        )

        val getByName = safeEndpointWithQueryBuilder("files/get-by-name") {
            resp(FileInfo::class)
            query(MediaSearchQuery::class)
        }

        @Serializable
        class QuotaQuery(
            val objectId: PrimaryKey,
            val objectType: ObjectType,
            val quotaType: QuotaType = QuotaType.FILE
        )

        val quota = safeEndpointWithQueryBuilder("files/quota") {
            resp(QuotaInfo::class)
            query(QuotaQuery::class)
        }

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

            val init = mutationEndpointBuilder("files/chunk/init") {
                resp(InitResponse::class)
                body(InitBody::class)
            }

            @Serializable
            class UploadPath(
                val id: PrimaryKey,
                val index: Int
            )

            val upload =
                mutationEndpointWithQueryAndPathBuilder("files/chunk/{id}/{index}/upload") {
                    resp(Unit::class)
                    body(Unit::class)
                    query(UploadQuery::class)
                    path(UploadPath::class)
                }

            val complete = mutationEndpointWithPathBuilder("files/chunk/{id}/complete") {
                resp(FileInfo::class)
                body(Unit::class)
                path(CommonPath::class)
            }
            val abort = mutationEndpointWithPathBuilder("files/chunk/{id}/abort") {
                resp(Unit::class)
                body(Unit::class)
                path(CommonPath::class)
            }

            @Serializable
            class StatusResponse(
                val uploaded: List<Int>,
                val chunkSize: Long,
                val size: Long,
                val id: PrimaryKey,
                val status: UploadRecordStatus
            )

            val status = safeEndpointWithPathBuilder("files/chunk/{id}/status") {
                resp(StatusResponse::class)
                path(CommonPath::class)
            }
        }
    }

    object Titles {
        object Id {
            object Favorite {
                val add = mutationEndpointWithPathBuilder("titles/{id}/favorite") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "titles/{id}/favorite",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }

            object Subscription {
                val add = mutationEndpointWithPathBuilder("titles/{id}/subscription") {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
                val delete = mutationEndpointWithPathBuilder(
                    "titles/{id}/subscription",
                    methodType = MutationMethodType.DELETE
                ) {
                    resp(Unit::class)
                    body(Unit::class)
                    path(CommonPath::class)
                }
            }
        }

        val add = mutationEndpointBuilder("titles") {
            resp(TitleInfo::class)
            body(NewTitle::class)
        }
    }

    object Accounts {
        val signIn = mutationEndpointBuilder("/accounts/sign-in") {
            resp(UserInfo::class)
            body(SignInBody::class)
        }
        val signOut = mutationEndpointBuilder("/accounts/sign-out") {
            resp(Unit::class)
            body(Unit::class)
        }
        val signUp = mutationEndpointBuilder("/accounts/sign-up") {
            resp(UserInfo::class)
            body(SignUpBody::class)
        }
        val getData = safeEndpointBuilder("/accounts/get-data") {
            resp(String::class)
        }

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
                val algoType: AlgoType = AlgoType.P256,
                val encryptedEncryptionPrivateKey: String? = null,
                val encryptionPublicKey: String? = null
            )

            val get =
                safeEndpointWithQueryBuilder("/accounts/child-accounts") {
                    resp(ChildAccountInfoListResponse::class)
                    query(ChildAccountQuery::class)
                }
            val add = mutationEndpointBuilder("/accounts/child-accounts") {
                resp(ChildAccountInfo::class)
                body(AddChildAccountRequest::class)
            }
        }
    }
}
