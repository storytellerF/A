package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.service.getMediaList
import com.storyteller_f.a.server.service.uploadMedia
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import org.apache.tika.Tika
import java.io.File

fun Route.bindProtectedSafeMediaRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteMedia> {
        usePrincipal(reader) { uid ->
            getMediaList(backend, uid, it)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "atemp")
    if (!root.exists() && !root.mkdir()) {
        error("create atemp failed")
    }

    val tika by lazy {
        Tika()
    }

    post<RouteMedia.Upload> {
        usePrincipal(reader) { uid ->
            uploadMedia(backend, it, uid, root, tika)
        }
    }
}
