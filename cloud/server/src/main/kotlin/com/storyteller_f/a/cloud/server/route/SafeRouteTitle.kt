package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.api.server.receiveBody
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.cloud.core.service.createTitle
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

fun Route.bindProtectedTitleRoute(backend: Backend) {
    CustomApi.Titles.add.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            val title = with(it) { receiveBody() }
            createTitle(title, backend, uid)
        }
    }
}
