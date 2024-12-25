package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.service.getMediaList
import com.storyteller_f.a.server.service.uploadMedia
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import java.io.File

fun Route.bindProtectedSafeMediaRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteMedia> {
        usePrincipal(reader) { id ->
            getMediaList(id, backend, it)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "atemp")
    if (!root.exists() && !root.mkdir()) {
        error("create atemp failed")
    }

    post<RouteMedia.Upload> {
        usePrincipal(reader) { id ->
            uploadMedia(it, id, root, backend)
        }
    }
}
