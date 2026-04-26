package com.storyteller_f.a.api

import com.storyteller_f.endpoint4k.common.body
import com.storyteller_f.endpoint4k.common.mutationEndpointBuilder
import com.storyteller_f.endpoint4k.common.mutationEndpointWithPathBuilder
import com.storyteller_f.endpoint4k.common.path
import com.storyteller_f.endpoint4k.common.query
import com.storyteller_f.endpoint4k.common.resp
import com.storyteller_f.endpoint4k.common.safeEndpointBuilder
import com.storyteller_f.endpoint4k.common.safeEndpointWithPathBuilder
import com.storyteller_f.endpoint4k.common.safeEndpointWithQueryAndPathBuilder
import com.storyteller_f.endpoint4k.common.safeEndpointWithQueryBuilder
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.FileRefInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.PanelLogInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.obj.ListResponse
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.UpdateObjectStatusBody
import com.storyteller_f.shared.obj.UpdateUserStatusBody
import com.storyteller_f.shared.type.CustomImmutableList
import kotlinx.serialization.Serializable

@Serializable
data class UserInfoListResponse(
    override val data: CustomImmutableList<UserInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<UserInfo>

@Serializable
data class CommunityInfoListResponse(
    override val data: CustomImmutableList<CommunityInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<CommunityInfo>

@Serializable
data class RoomInfoListResponse(
    override val data: CustomImmutableList<RoomInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<RoomInfo>

@Serializable
data class TitleInfoListResponse(
    override val data: CustomImmutableList<TitleInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<TitleInfo>

@Serializable
data class FileInfoListResponse(
    override val data: CustomImmutableList<FileInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<FileInfo>

@Serializable
data class UserLogInfoListResponse(
    override val data: CustomImmutableList<UserLogInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<UserLogInfo>

@Serializable
data class UploadRecordInfoListResponse(
    override val data: CustomImmutableList<com.storyteller_f.shared.model.UploadRecordInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<com.storyteller_f.shared.model.UploadRecordInfo>

@Serializable
data class ReactionRecordInfoListResponse(
    override val data: CustomImmutableList<ReactionRecordInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<ReactionRecordInfo>

@Serializable
data class TopicInfoListResponse(
    override val data: CustomImmutableList<TopicInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<TopicInfo>

@Serializable
data class UserFavoriteInfoListResponse(
    override val data: CustomImmutableList<UserFavoriteInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<UserFavoriteInfo>

@Serializable
data class UserSubscriptionInfoListResponse(
    override val data: CustomImmutableList<UserSubscriptionInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<UserSubscriptionInfo>

@Serializable
data class MemberInfoListResponse(
    override val data: CustomImmutableList<MemberInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<MemberInfo>

@Serializable
data class FileRefInfoListResponse(
    override val data: CustomImmutableList<FileRefInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<FileRefInfo>

@Serializable
data class PanelLogInfoListResponse(
    override val data: CustomImmutableList<PanelLogInfo>,
    override val pagination: Pagination<String>? = null
) :
    ListResponse<PanelLogInfo>

object AdminApi {
    object Users {
        val get = safeEndpointWithQueryBuilder("/admin/users") {
            resp(UserInfoListResponse::class)
            query(PaginationQuery::class)
        }
        val add = mutationEndpointBuilder("/admin/users") {
            resp(UserInfo::class)
            body(NewUser::class)
        }

        object Id {
            val get = safeEndpointWithPathBuilder("/admin/users/{id}") {
                resp(UserInfo::class)
                path(CommonPath::class)
            }

            object Overview {
                val get = safeEndpointWithPathBuilder("/admin/users/{id}/overview") {
                    resp(UserOverview::class)
                    path(CommonPath::class)
                }
            }

            object Status {
                val update = mutationEndpointWithPathBuilder("/admin/users/{id}/status") {
                    resp(Unit::class)
                    body(UpdateUserStatusBody::class)
                    path(CommonPath::class)
                }
            }

            object Communities {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/communities") {
                    resp(CommunityInfoListResponse::class)
                    query(CustomApi.Users.JoinedCommunities.UserCommunitiesQuery::class)
                    path(CommonPath::class)
                }
            }

            object Rooms {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/rooms") {
                    resp(RoomInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object Titles {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/titles") {
                    resp(TitleInfoListResponse::class)
                    query(CustomApi.Users.Id.Titles.TitleQuery::class)
                    path(CommonPath::class)
                }
            }

            object Files {
                val get =
                    safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/files") {
                        resp(FileInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }
            }

            object Logs {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/logs") {
                    resp(UserLogInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object UploadRecords {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/upload-records") {
                    resp(UploadRecordInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object Reactions {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/reactions") {
                    resp(ReactionRecordInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object Comments {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/comments") {
                    resp(TopicInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object Favorites {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/favorites") {
                    resp(UserFavoriteInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object Subscriptions {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/users/{id}/subscriptions") {
                    resp(UserSubscriptionInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }
        }
    }

    val signIn = mutationEndpointBuilder("/admin/sign-in") {
        resp(PanelAccountInfo::class)
        body(SignInBody::class)
    }
    val signOut = mutationEndpointBuilder("/admin/sign-out") {
        resp(Unit::class)
        body(Unit::class)
    }
    val signUp = mutationEndpointBuilder("/admin/sign-up") {
        resp(PanelAccountInfo::class)
        body(SignUpBody::class)
    }
    val getData = safeEndpointBuilder("/admin/get-data") {
        resp(String::class)
    }
    val overview = safeEndpointBuilder("/admin/overview") {
        resp(PanelOverview::class)
    }

    object PanelLogs {
        val get = safeEndpointWithQueryBuilder("/admin/panel-logs") {
            resp(PanelLogInfoListResponse::class)
            query(PanelLogsQuery::class)
        }
    }

    object Communities {
        val get = safeEndpointWithQueryBuilder("/admin/communities") {
            resp(CommunityInfoListResponse::class)
            query(PaginationQuery::class)
        }

        object Id {
            val get = safeEndpointWithPathBuilder("/admin/communities/{id}") {
                resp(CommunityInfo::class)
                path(CommonPath::class)
            }

            object Status {
                val update = mutationEndpointWithPathBuilder("/admin/communities/{id}/status") {
                    resp(Unit::class)
                    body(UpdateObjectStatusBody::class)
                    path(CommonPath::class)
                }
            }

            object Members {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/communities/{id}/members") {
                    resp(MemberInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }
        }
    }

    object Rooms {
        val getPublic = safeEndpointWithQueryBuilder("/admin/rooms/public") {
            resp(RoomInfoListResponse::class)
            query(PaginationQuery::class)
        }
        val getPrivate = safeEndpointWithQueryBuilder("/admin/rooms/private") {
            resp(RoomInfoListResponse::class)
            query(PaginationQuery::class)
        }

        object Id {
            val get = safeEndpointWithPathBuilder("/admin/rooms/{id}") {
                resp(RoomInfo::class)
                path(CommonPath::class)
            }

            object Status {
                val update = mutationEndpointWithPathBuilder("/admin/rooms/{id}/status") {
                    resp(Unit::class)
                    body(UpdateObjectStatusBody::class)
                    path(CommonPath::class)
                }
            }

            object Members {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/rooms/{id}/members") {
                    resp(MemberInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }

            object Files {
                val get = safeEndpointWithQueryAndPathBuilder("/admin/rooms/{id}/files") {
                    resp(FileInfoListResponse::class)
                    query(PaginationQuery::class)
                    path(CommonPath::class)
                }
            }
        }
    }

    object Topics {
        val get = safeEndpointWithQueryBuilder("/admin/topics") {
            resp(TopicInfoListResponse::class)
            query(PaginationQuery::class)
        }

        object Id {
            val get = safeEndpointWithPathBuilder("/admin/topics/{id}") {
                resp(TopicInfo::class)
                path(CommonPath::class)
            }

            object Status {
                val update = mutationEndpointWithPathBuilder("/admin/topics/{id}/status") {
                    resp(Unit::class)
                    body(UpdateObjectStatusBody::class)
                    path(CommonPath::class)
                }
            }

            object Topics {
                val get =
                    safeEndpointWithQueryAndPathBuilder("/admin/topics/{id}/topics") {
                        resp(TopicInfoListResponse::class)
                        query(TopicQuery::class)
                        path(CommonPath::class)
                    }
            }
        }
    }

    object Titles {
        val get = safeEndpointWithQueryBuilder("/admin/titles") {
            resp(TitleInfoListResponse::class)
            query(PaginationQuery::class)
        }

        object Id {
            val get = safeEndpointWithPathBuilder("/admin/titles/{id}") {
                resp(TitleInfo::class)
                path(CommonPath::class)
            }

            object Status {
                val update = mutationEndpointWithPathBuilder("/admin/titles/{id}/status") {
                    path(CommonPath::class)
                    body(UpdateObjectStatusBody::class)
                }
            }
        }
    }

    object Files {
        val get = safeEndpointWithQueryBuilder("/admin/files") {
            resp(FileInfoListResponse::class)
            query(PaginationQuery::class)
        }

        val search = safeEndpointWithQueryBuilder("/admin/files/search") {
            resp(FileInfoListResponse::class)
            query(SearchQuery::class)
        }

        object Id {
            val get = safeEndpointWithPathBuilder("/admin/files/{id}") {
                resp(FileInfo::class)
                path(CommonPath::class)
            }

            object Status {
                val update = mutationEndpointWithPathBuilder("/admin/files/{id}/status") {
                    resp(Unit::class)
                    body(UpdateObjectStatusBody::class)
                    path(CommonPath::class)
                }
            }

            object Refs {
                val get =
                    safeEndpointWithQueryAndPathBuilder("/admin/files/{id}/refs") {
                        resp(FileRefInfoListResponse::class)
                        query(PaginationQuery::class)
                        path(CommonPath::class)
                    }
            }
        }
    }
}
