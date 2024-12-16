package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.service.getMediaList
import com.storyteller_f.media.UploadPack
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
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
            var fileName = ""
            val multipartData = call.receiveMultipart()

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName as String
                        val fileBytes = part.provider().readRemaining().readByteArray()
                        val file = File(root, fileName)
                        file.writeBytes(fileBytes)
                        backend.mediaService.upload("amedia", listOf(UploadPack("$id/$fileName", file)))
                    }

                    else -> {}
                }
                part.dispose()
            }

            Result.success(Unit)
        }
    }
}
