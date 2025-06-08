@file:Suppress("detekt.formatting")

package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.common.PathResponse
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.media.FileSystemMediaService
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.io.path.exists

fun Routing.bindUnauthenticatedRoute(reader: DatabaseReader, backend: Backend) {
    get("/ping") {
        call.respondText("pong")
    }

    get("/amedia/{path...}") {
        omitPrincipal(reader) {
            checkParameter<List<String>, PathResponse>("path") { paths ->
                val service = backend.mediaService
                if (service is FileSystemMediaService) {
                    val path = service.getPathResponse(paths)
                    if (path?.exists() == true) {
                        val value = PathResponse(path)
                        Result.success(value)
                    } else {
                        Result.success(null)
                    }
                } else {
                    Result.failure(BadRequestException("can't find file"))
                }
            }
        }
    }

    get {
        call.respondText("${backend.config.flavor} ${backend.config.buildType}")
    }
}
