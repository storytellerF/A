package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.PathResponse
import com.storyteller_f.a.cloud.core.service.getFileSystemDownloadUrl
import com.storyteller_f.a.cloud.server.auth.callRespond
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.common.checkParameter
import com.storyteller_f.endpoint4k.ktor.server.invoke
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.bindUnauthenticatedRoute(backend: Backend) {
    get("/ping") {
        call.respondText("pong")
    }

    get("/a_file/{path...}") {
        callRespond {
            checkParameter<List<String>, PathResponse>("path") { paths ->
                getFileSystemDownloadUrl(backend, paths)
            }
        }
    }

    CustomApi.Root.get(handleResult()) {
        Result.success("${backend.customConfig.flavor} ${backend.customConfig.buildType}")
    }
}
