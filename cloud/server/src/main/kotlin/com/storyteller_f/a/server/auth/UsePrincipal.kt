package com.storyteller_f.a.server.auth

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.server.ServerConfig
import com.storyteller_f.a.server.common.FileResponse
import com.storyteller_f.a.server.common.PathResponse
import com.storyteller_f.backend.service.CustomBadRequestException
import com.storyteller_f.backend.service.ForbiddenException
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UnauthorizedException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.io.path.name

suspend inline fun <reified R : Any> RoutingContext.usePrincipal(
    reader: DatabaseReader,
    block: (PrimaryKey) -> Result<R?>
) {
    usePrincipalOrNull(reader) { uid ->
        if (uid != null) {
            block(uid)
        } else {
            Result.failure(UnauthorizedException())
        }
    }
}

suspend inline fun <reified R : Any> RoutingContext.omitPrincipal(reader: DatabaseReader, block: () -> Result<R?>) {
    callRespond<R>({
        block()
    }, null, reader)
}

suspend inline fun <reified R : Any> RoutingContext.usePrincipalOrNull(
    reader: DatabaseReader,
    block: (PrimaryKey?) -> Result<R?>?
) {
    val uid = call.principal<CustomPrincipal>()?.uid
    callRespond<R>(block, uid, reader)
}

suspend inline fun <reified R : Any> RoutingContext.callRespond(
    block: (PrimaryKey?) -> Result<R?>?,
    uid: PrimaryKey?,
    reader: DatabaseReader
) {
    try {
        val result = block(uid)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        result.onSuccess {
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
            if (!respondError(it, reader)) {
                call.application.log.error("Occur server exception", it)
            }
        }
    } catch (e: Exception) {
        call.application.log.error("Catch exception in api", e)
    }
}

suspend fun RoutingContext.respondError(e: Throwable, reader: DatabaseReader): Boolean {
    when (e) {
        is ForbiddenException -> {
            call.respond(HttpStatusCode.Forbidden, e.message.toString())
            return true
        }

        is UnauthorizedException -> {
            call.respondUnauthorizedResponse(reader)
            return true
        }

        is MissingRequestParameterException, is ParameterConversionException, is ContentTransformationException -> {
            call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
            return true
        }

        is BadRequestException, is CustomBadRequestException, is IllegalArgumentException, is IllegalStateException -> {
            call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
            return true
        }

        else -> {
            call.respond(
                HttpStatusCode.InternalServerError,
                if (ServerConfig.IS_PROD) "" else (e.message ?: e.toString())
            )
            return false
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
