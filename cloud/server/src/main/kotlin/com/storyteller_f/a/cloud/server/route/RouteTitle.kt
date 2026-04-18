package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.addFavorite
import com.storyteller_f.a.cloud.core.service.addSubscription
import com.storyteller_f.a.cloud.core.service.createTitle
import com.storyteller_f.a.cloud.core.service.deleteFavoriteByObject
import com.storyteller_f.a.cloud.core.service.removeSubscriptionByObject
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route

fun Route.bindProtectedTitleRoute(backend: Backend) {
    CustomApi.Titles.add(handleResult(backend)) {
        usePrincipal { uid ->
            val title = it.receiveBody()
            createTitle(title, backend, uid)
        }
    }

    CustomApi.Titles.Id.Favorite.add(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.addFavorite(uid, NewFavorite(ObjectType.TITLE, p.id)).map { }
        }
    }

    CustomApi.Titles.Id.Favorite.delete(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.deleteFavoriteByObject(uid, p.id).map { }
        }
    }

    CustomApi.Titles.Id.Subscription.add(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.addSubscription(uid, NewSubscription(p.id, ObjectType.TITLE)).map { }
        }
    }

    CustomApi.Titles.Id.Subscription.delete(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.removeSubscriptionByObject(uid, p.id).map { }
        }
    }
}
