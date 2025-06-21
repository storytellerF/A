package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import io.ktor.server.routing.*
import java.io.File

fun Route.bindProtectedSafeMediaRoute(backend: Backend) {
    CustomApi.Medias.get.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            it.pagination(IdentifiablePagingGenerator) { pagingFetch ->
                backend.getMediaList(uid, it, pagingFetch)
            }
        }
    }

    CustomApi.Medias.list.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            backend.getAllMediaList(uid, it)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "atemp")
    if (!root.exists() && !root.mkdir()) {
        error("create atemp failed")
    }

    CustomApi.Medias.Id.extractAlbum.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.extractAlbum(p.id, root, uid)
        }
    }

    CustomApi.Medias.upload.invoke(RoutingContext::handleResult) { q, api ->
        usePrincipal { uid ->
            uploadMedia(backend, uid, root, q)
        }
    }

    CustomApi.Medias.Id.copy.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.copyMedia(p, uid)
        }
    }
}
