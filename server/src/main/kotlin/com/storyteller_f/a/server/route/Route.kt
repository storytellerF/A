package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.UserSession
import com.storyteller_f.a.server.auth.bindUnauthenticatedRoute
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.service.getMediaList
import com.storyteller_f.a.server.webSocketContent
import com.storyteller_f.media.UploadPack
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import java.io.File

fun Application.commonRoute(backend: Backend) {
    routing {
        authenticate {
            bindProtectedSafeTopicRoute(backend)
            bindProtectedSafeCommunityRoute(backend)
            bindProtectedSafeRoomRoute(backend)
            bindProtectedSafeUserRoute()
            webSocket("/link") {
                webSocketContent(backend)
            }
            post("/sign_out") {
                usePrincipal { _ ->
                    call.sessions.clear(UserSession::class)
                    Result.success(Unit)
                }
            }
            bindMediaRoute(backend)
        }
        authenticate(optional = true) {
            bindSafeRoomRoute(backend)
            bindSafeTopicRoute(backend)
            bindSafeCommunityRoute(backend)
            bindSafeUserRoute(backend)
            bindProtectedSafeUserRoute()
        }
        bindUnauthenticatedRoute(backend)
    }
}

private fun Route.bindMediaRoute(backend: Backend) {
    get("/amedia") {
        usePrincipal {
            getMediaList(it, backend)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "atemp")
    if (!root.exists() && !root.mkdir()) {
        error("create atemp failed")
    }

    post("/upload") {
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
