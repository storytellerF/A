package com.storyteller_f.a.server.auth

import com.storyteller_f.a.api.server.handleCaughtException
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.server.ServerConfig
import com.storyteller_f.a.server.common.FileResponse
import com.storyteller_f.a.server.common.PathResponse
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.io.path.name

suspend inline fun <reified R : Any> RoutingContext.omitPrincipal(block: () -> Result<R?>) = callRespond<R>(block)

inline fun <reified R : Any> RoutingContext.usePrincipal(
    block: (uid: PrimaryKey) -> Result<R?>,
) = usePrincipalOrNull { uid ->
    if (uid != null) {
        block(uid)
    } else {
        Result.failure(UnauthorizedException())
    }
}

inline fun <reified R : Any> RoutingContext.usePrincipalOrNull(
    block: (uid: PrimaryKey?) -> Result<R?>?,
) = block(call.principal<CustomPrincipal>()?.uid)

suspend inline fun <reified R : Any> RoutingContext.callRespond(
    block: () -> Result<R?>?,
) {
    try {
        val result = block()
        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        handleResult(result)
    } catch (e: Exception) {
        handleCaughtException(e)
    }
}

suspend fun RoutingContext.respondError(e: Throwable) {
    when (e) {
        is ForbiddenException -> {
            call.respond(HttpStatusCode.Forbidden, e.message.toString())
        }

        is UnauthorizedException -> {
            call.respondUnauthorizedResponse()
        }

        is MissingRequestParameterException, is ParameterConversionException, is ContentTransformationException -> {
            call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
        }

        is BadRequestException, is CustomBadRequestException, is IllegalArgumentException, is IllegalStateException -> {
            call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
        }

        else -> {
            call.respond(
                HttpStatusCode.InternalServerError,
                if (ServerConfig.IS_PROD) "" else (e.message ?: e.toString())
            )
        }
    }
}

inline fun <reified R : Any> DefaultWebSocketServerSession.usePrincipalOrNull(block: (PrimaryKey?) -> R?) {
    val uid = call.principal<CustomPrincipal>()?.uid
    if (uid == null) {
        block(null)
    } else {
        block(uid)
    }
}

suspend inline fun <reified R> RoutingContext.handleResult(it: Result<R>) {
    it.onSuccess {
        when (it) {
            null -> call.respond(HttpStatusCode.NotFound)
            is FileResponse -> {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        it.file.name
                    )
                        .toString()
                )
                call.respondFile(it.file)
            }

            is PathResponse -> {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        it.file.name
                    )
                        .toString()
                )
                call.respondPath(it.file)
            }

            is Unit -> call.respond(HttpStatusCode.OK)
            else -> call.respond(it)
        }
    }.onFailure {
        respondError(it)
        call.application.log.error("Occur server exception", it)
    }
}
