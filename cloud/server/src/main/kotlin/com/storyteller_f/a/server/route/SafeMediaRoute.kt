package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.Api
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipal1
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.shared.obj.NewMedia
import com.storyteller_f.shared.obj.ObjectTuple
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import java.io.File

fun Route.bindProtectedSafeMediaRoute(backend: Backend) {
    Api.Medias.get.invoke(RoutingContext::handleResult) {
        usePrincipal1 { uid ->
            it.pagination(IdentifiablePagingGenerator) { pagingFetch ->
                backend.getMediaList(uid, it, pagingFetch)
            }
        }
    }

    get<RouteMedia.All> {
        usePrincipal { uid ->
            backend.getAllMediaList(uid, it.parent)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "atemp")
    if (!root.exists() && !root.mkdir()) {
        error("create atemp failed")
    }

    post<RouteMedia.Id.ExtractAlbum> {
        usePrincipal { uid ->
            backend.extractAlbum(it.parent.id, root, uid)
        }
    }

    post<RouteMedia.Upload> {
        usePrincipal { uid ->
            uploadMedia(backend, it, uid, root)
        }
    }

    post<RouteMedia.Copy> {
        usePrincipal { uid ->
            val newMedia = call.receive<NewMedia>()
            val objectId = it.parent.objectId
            val objectType = it.parent.objectType
            if (objectType == null || objectId == null) error("invalid query")
            backend.copyMedia(newMedia.noPrefixName, uid, ObjectTuple(objectId, objectType))
        }
    }
}
