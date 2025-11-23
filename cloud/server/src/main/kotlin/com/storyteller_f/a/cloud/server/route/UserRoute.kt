package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.addDevice
import com.storyteller_f.a.cloud.core.service.addFavorite
import com.storyteller_f.a.cloud.core.service.addReadLog
import com.storyteller_f.a.cloud.core.service.addSubscription
import com.storyteller_f.a.cloud.core.service.deleteFavorite
import com.storyteller_f.a.cloud.core.service.getFavorites
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.getUserInfo
import com.storyteller_f.a.cloud.core.service.getUserOverview
import com.storyteller_f.a.cloud.core.service.getUserSubscriptions
import com.storyteller_f.a.cloud.core.service.getUserTitles
import com.storyteller_f.a.cloud.core.service.removeSubscription
import com.storyteller_f.a.cloud.core.service.searchUsers
import com.storyteller_f.a.cloud.core.service.updateUser
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.common.pagingGenerator
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route

fun Route.bindProtectedUserRoute(backend: Backend) {
    CustomApi.Users.update(handleResult()) { api ->
        usePrincipal { uid ->
            backend.updateUser(uid, api.receiveBody())
        }
    }
    CustomApi.Users.Read.add(handleResult()) { api ->
        usePrincipal { uid ->
            backend.addReadLog(uid, api.receiveBody())
        }
    }
    CustomApi.Users.Devices.add(handleResult()) { api ->
        usePrincipal { uid ->
            val newDevice = api.receiveBody()
            backend.addDevice(uid, newDevice)
        }
    }
    CustomApi.Favorites.add(handleResult()) { api ->
        usePrincipal { uid ->
            backend.addFavorite(uid, api.receiveBody())
        }
    }
    CustomApi.Favorites.delete(handleResult()) { path, _ ->
        usePrincipal { uid ->
            backend.deleteFavorite(uid, path.id)
        }
    }
    CustomApi.Favorites.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(pagingGenerator {
                it.id
            }) { fetch ->
                backend.getFavorites(uid, fetch)
            }
        }
    }
    CustomApi.Users.overview(handleResult()) {
        usePrincipal { uid ->
            backend.getUserOverview(uid)
        }
    }
}

fun Route.bindProtectedUserSubscriptionRoute(backend: Backend) {
    CustomApi.Subscriptions.add(handleResult()) { api ->
        usePrincipal { uid ->
            backend.addSubscription(uid, api.receiveBody())
        }
    }

    CustomApi.Subscriptions.delete(handleResult()) { path, _ ->
        usePrincipal { uid ->
            backend.removeSubscription(uid, path.id)
        }
    }

    CustomApi.Subscriptions.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(pagingGenerator {
                it.id
            }) { fetch ->
                backend.getUserSubscriptions(uid, fetch)
            }
        }
    }
}

fun Route.bindUserRoute(backend: Backend) {
    CustomApi.Users.Aid.get(handleResult()) { q ->
        backend.getUserInfo(ObjectFetch.AidFetch(q.aid))
    }
    CustomApi.Users.Id.get(handleResult()) {
        backend.getUserInfo(ObjectFetch.IdFetch(it.id))
    }

    CustomApi.Users.Id.Topics.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopicsByParentId(
                    p.id,
                    ObjectType.USER,
                    uid,
                    q.fillHasCommented,
                    f,
                    q.pinType
                )
            }
        }
    }

    CustomApi.Users.Id.Titles.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, f)
        }
    }

    CustomApi.Users.search(handleResult()) {
        it.pagination(IdentifiablePagingGenerator) { f ->
            backend.searchUsers(it.word, f)
        }
    }
}
