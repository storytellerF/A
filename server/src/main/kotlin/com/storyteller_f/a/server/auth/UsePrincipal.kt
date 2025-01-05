package com.storyteller_f.a.server.auth

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.CustomBadRequestException
import com.storyteller_f.ForbiddenException
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.shared.model.MediaResponse
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import java.io.File

suspend inline fun <reified R : Any> RoutingContext.usePrincipal(
    reader: DatabaseReader,
    block: (PrimaryKey) -> Result<R?>
) {
    usePrincipalOrNull(reader) {
        if (it != null) {
            block(it)
        } else {
            Result.failure(UnauthorizedException())
        }
    }
}

suspend inline fun <reified R : Any> RoutingContext.omitPrincipal(reader: DatabaseReader, block: () -> Result<R?>) {
    usePrincipalOrNull(reader) {
        block()
    }
}

suspend inline fun <reified R : Any> RoutingContext.usePrincipalOrNull(
    reader: DatabaseReader,
    block: (PrimaryKey?) -> Result<R?>
) {
    val uid = call.principal<CustomPrincipal>()?.uid
    // 有可能存在bug 导致block 抛出异常，所以需要进行一次try catch
    try {
        block(uid).onSuccess {
            when (it) {
                null -> call.respond(HttpStatusCode.NotFound)
                is MediaResponse -> call.respondFile(File(it.file))
                is Unit -> call.respond(HttpStatusCode.OK)
                else -> call.respond(it)
            }
        }.onFailure {
            if (!respondError(it, reader)) {
                call.application.log.error("Occur exception", it)
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

        is BadRequestException, is CustomBadRequestException -> {
            call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
            return true
        }

        else -> {
            call.respond(HttpStatusCode.InternalServerError, e.localizedMessage ?: e.toString())
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
