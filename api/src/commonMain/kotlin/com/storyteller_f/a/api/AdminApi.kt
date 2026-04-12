package com.storyteller_f.a.api

import com.storyteller_f.endpoint4k.common.mutationEndpoint
import com.storyteller_f.endpoint4k.common.mutationEndpointWithPath
import com.storyteller_f.endpoint4k.common.safeEndpoint
import com.storyteller_f.endpoint4k.common.safeEndpointWithPath
import com.storyteller_f.endpoint4k.common.safeEndpointWithQuery
import com.storyteller_f.endpoint4k.common.safeEndpointWithQueryAndPath
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.FileRefInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PanelAccountInfo
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
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.UpdateObjectStatusBody
import com.storyteller_f.shared.obj.UpdateUserStatusBody


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
                        CustomApi.Users.JoinedCommunities.UserCommunitiesQuery,
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

            object Status {
                val update = mutationEndpointWithPath<Unit, UpdateObjectStatusBody, CommonPath>(
                    "/admin/communities/{id}/status"
                )
            }

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

            object Status {
                val update = mutationEndpointWithPath<Unit, UpdateObjectStatusBody, CommonPath>(
                    "/admin/rooms/{id}/status"
                )
            }

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

            object Status {
                val update = mutationEndpointWithPath<Unit, UpdateObjectStatusBody, CommonPath>(
                    "/admin/topics/{id}/status"
                )
            }

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

            object Status {
                val update = mutationEndpointWithPath<Unit, UpdateObjectStatusBody, CommonPath>(
                    "/admin/titles/{id}/status"
                )
            }
        }
    }

    object Files {
        val get = safeEndpointWithQuery<ServerResponse<FileInfo>, PaginationQuery>("/admin/files")

        val search = safeEndpointWithQuery<ServerResponse<FileInfo>, SearchQuery>("/admin/files/search")

        object Id {
            val get = safeEndpointWithPath<FileInfo, CommonPath>("/admin/files/{id}")

            object Status {
                val update = mutationEndpointWithPath<Unit, UpdateObjectStatusBody, CommonPath>(
                    "/admin/files/{id}/status"
                )
            }

            object Refs {
                val get =
                    safeEndpointWithQueryAndPath<ServerResponse<FileRefInfo>, PaginationQuery, CommonPath>(
                        "/admin/files/{id}/refs"
                    )
            }
        }
    }
}