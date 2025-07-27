package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.api.server.receiveBody
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.getUserInfo
import com.storyteller_f.a.backend.service.searchMembers
import com.storyteller_f.a.cloud.core.service.addDevice
import com.storyteller_f.a.cloud.core.service.addReadLog
import com.storyteller_f.a.cloud.core.service.getTopLevelTopicsInObject
import com.storyteller_f.a.cloud.core.service.getUserTitles
import com.storyteller_f.a.cloud.core.service.updateUser
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.*

fun Route.bindProtectedUserRoute(backend: Backend) {
    CustomApi.Users.update.invoke(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            backend.updateUser(uid, with(api) { receiveBody() })
        }
    }
    CustomApi.Users.Read.add.invoke(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            backend.addReadLog(uid, with(api) { receiveBody() })
        }
    }
    CustomApi.Users.Devices.add.invoke(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            val newDevice = with(api) { receiveBody() }
            addDevice(backend, uid, newDevice)
        }
    }
}

fun Route.bindUserRoute(backend: Backend) {
    CustomApi.Users.Aid.get.invoke(RoutingContext::handleResult) {
        backend.getUserInfo(ObjectFetch.AidFetch(it.aid))
    }
    CustomApi.Users.Id.get.invoke(RoutingContext::handleResult) {
        backend.getUserInfo(ObjectFetch.IdFetch(it.id))
    }

    CustomApi.Users.Id.Topics.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
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

    CustomApi.Users.Id.Titles.get.invoke(RoutingContext::handleResult) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, f)
        }
    }

    CustomApi.Users.search.invoke(RoutingContext::handleResult) {
        it.pagination(IdentifiablePagingGenerator) { f ->
            backend.searchMembers(null, it.word, f)
        }
    }
}
