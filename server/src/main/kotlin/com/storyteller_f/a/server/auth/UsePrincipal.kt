package com.storyteller_f.a.server.auth

import com.storyteller_f.a.server.service.ForbiddenException
import com.storyteller_f.shared.type.OKey
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.*


suspend inline fun <reified R : Any> RoutingContext.usePrincipal(block: (OKey) -> Result<R?>) {
    usePrincipalOrNull {
        if (it != null) {
            block(it)
        } else {
            Result.failure(UnauthorizedException())
        }
    }
}

class UnauthorizedException : Exception()

suspend inline fun <reified R : Any> RoutingContext.omitPrincipal(block: () -> Result<R?>) {
    usePrincipalOrNull {
        block()
    }
}

suspend inline fun <reified R : Any> RoutingContext.usePrincipalOrNull(block: (OKey?) -> Result<R?>) {
    val uid = call.principal<CustomPrincipal>()?.uid
    try {
        block(uid).onSuccess {
            when (it) {
                null -> call.respond(HttpStatusCode.NotFound)
                is Unit -> call.respond(HttpStatusCode.OK)
                else -> call.respond(it)
            }
        }.onFailure {
            respondError(it)
        }
    } catch (e: Exception) {
        application.log.error(e)
    }

}

suspend fun RoutingContext.respondError(e: Throwable) {
    when (e) {
        is ForbiddenException -> call.respond(HttpStatusCode.Forbidden, e.message.toString())
        is UnauthorizedException -> call.respond(HttpStatusCode.Unauthorized)
        is MissingRequestParameterException, is ParameterConversionException, is ContentTransformationException ->
            call.respond(
                HttpStatusCode.BadRequest, e.localizedMessage
            )

        else -> call.respond(HttpStatusCode.InternalServerError, e.localizedMessage ?: e.toString())
    }
}

inline fun <reified R : Any> DefaultWebSocketServerSession.usePrincipalOrNull(block: (OKey?) -> R?) {
    val uid = call.principal<CustomPrincipal>()?.uid
    if (uid == null) {
        block(null)
    } else {
        block(uid)
    }
}
