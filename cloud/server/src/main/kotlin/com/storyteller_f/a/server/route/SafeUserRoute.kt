package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.api.server.receiveBody
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.exposed.isDup
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.addReadLog
import com.storyteller_f.a.server.service.getTopLevelTopicsInObject
import com.storyteller_f.a.server.service.getUserTitles
import com.storyteller_f.a.server.service.updateUser
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.getUserInfo
import com.storyteller_f.backend.service.searchMembers
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.recoverResult
import io.ktor.server.routing.*

fun Route.bindProtectedSafeUserRoute(backend: Backend) {
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
            backend.exposedDatabase.userDatabase.addDevice(uid, newDevice.endpointUrl).recoverResult {
                if (it.isDup()) {
                    Result.success(Unit)
                } else {
                    Result.failure(it)
                }
            }
        }
    }
}

fun Route.bindSafeUserRoute(backend: Backend) {
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

    CustomApi.Users.Search.get.invoke(RoutingContext::handleResult) {
        it.pagination(IdentifiablePagingGenerator) { f ->
            backend.searchMembers(null, it.word, f)
        }
    }
}
