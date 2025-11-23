package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.createTitle
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import io.ktor.server.routing.Route

fun Route.bindProtectedTitleRoute(backend: Backend) {
    CustomApi.Titles.add(handleResult()) {
        usePrincipal { uid ->
            val title = it.receiveBody()
            createTitle(title, backend, uid)
        }
    }
}
