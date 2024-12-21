package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.service.getMediaList
import com.storyteller_f.a.server.service.uploadMedia
import io.ktor.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.io.File

fun Route.bindProtectedSafeMediaRoute(backend: Backend) {
    get<RouteMedia> {
        usePrincipal { id ->
            getMediaList(id, backend, it)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "atemp")
    if (!root.exists() && !root.mkdir()) {
        error("create atemp failed")
    }

    post<RouteMedia.Upload> {
        usePrincipal { id ->
            uploadMedia(it, id, root, backend)
        }
    }
}
