package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.PathResponse
import com.storyteller_f.a.cloud.core.service.getFileSystemDownloadUrl
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.omitPrincipal
import com.storyteller_f.a.cloud.server.common.checkParameter
import com.storyteller_f.route4k.ktor.server.invoke
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.bindUnauthenticatedRoute(backend: Backend) {
    get("/ping") {
        call.respondText("pong")
    }

    get("/a_file/{path...}") {
        omitPrincipal {
            checkParameter<List<String>, PathResponse>("path") { paths ->
                getFileSystemDownloadUrl(backend, paths)
            }
        }
    }

    CustomApi.Root.get(RoutingContext::handleResult) {
        Result.success("${backend.customConfig.flavor} ${backend.customConfig.buildType}")
    }
}
