package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
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
import com.storyteller_f.a.cloud.core.service.getUserSubscriptions
import com.storyteller_f.a.cloud.core.service.getUserTitles
import com.storyteller_f.a.cloud.core.service.removeSubscription
import com.storyteller_f.a.cloud.core.service.searchMembers
import com.storyteller_f.a.cloud.core.service.updateUser
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.common.pagingGenerator
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.route4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

fun Route.bindProtectedUserRoute(backend: Backend) {
    CustomApi.Users.update(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            backend.updateUser(uid, api.receiveBody())
        }
    }
    CustomApi.Users.Read.add(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            backend.addReadLog(uid, api.receiveBody())
        }
    }
    CustomApi.Users.Devices.add(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            val newDevice = api.receiveBody()
            backend.addDevice(uid, newDevice)
        }
    }
    CustomApi.Favorites.add(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            backend.addFavorite(uid, api.receiveBody())
        }
    }
    CustomApi.Favorites.delete(RoutingContext::handleResult) { path, api ->
        usePrincipal { uid ->
            backend.deleteFavorite(uid, path.id)
        }
    }
    CustomApi.Favorites.get(RoutingContext::handleResult) { q ->
        usePrincipal { uid ->
            q.pagination(pagingGenerator {
                it.id
            }) { fetch ->
                backend.getFavorites(uid, fetch)
            }
        }
    }
    CustomApi.Subscriptions.add(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            backend.addSubscription(uid, api.receiveBody())
        }
    }

    CustomApi.Subscriptions.delete(RoutingContext::handleResult) { path, api ->
        usePrincipal { uid ->
            backend.removeSubscription(uid, path.id)
        }
    }

    CustomApi.Subscriptions.get(RoutingContext::handleResult) { q ->
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
    CustomApi.Users.Aid.get(RoutingContext::handleResult) {
        backend.getUserInfo(ObjectFetch.AidFetch(it.aid))
    }
    CustomApi.Users.Id.get(RoutingContext::handleResult) {
        backend.getUserInfo(ObjectFetch.IdFetch(it.id))
    }

    CustomApi.Users.Id.Topics.get(RoutingContext::handleResult) { q, p ->
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

    CustomApi.Users.Id.Titles.get(RoutingContext::handleResult) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, f)
        }
    }

    CustomApi.Users.search(RoutingContext::handleResult) {
        it.pagination(IdentifiablePagingGenerator) { f ->
            backend.searchMembers(null, it.word, f)
        }
    }
}
