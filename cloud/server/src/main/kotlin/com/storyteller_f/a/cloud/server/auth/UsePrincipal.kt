package com.storyteller_f.a.cloud.server.auth

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.cloud.core.service.FileResponse
import com.storyteller_f.a.cloud.core.service.PathResponse
import com.storyteller_f.endpoint4k.ktor.server.handleCaughtException
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.request.queryString
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondPath
import io.ktor.server.routing.RoutingContext
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.util.toMap
import io.sentry.Sentry
import io.sentry.protocol.Request
import kotlin.io.path.name

inline fun <reified R : Any> omitPrincipal(block: () -> Result<R?>) = block()

inline fun <reified R : Any> RoutingContext.usePrincipal(
    block: (uid: PrimaryKey) -> Result<R?>,
) = usePrincipalOrNull { uid ->
    if (uid != null) {
        block(uid)
    } else {
        Result.failure(UnauthorizedException())
    }
}

suspend inline fun <reified R : Any> RoutingContext.callRespond(
    backend: Backend,
    block: () -> Result<R?>?,
) {
    try {
        val result = block()
        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        handleResultInternal(result, backend.customConfig.buildType)
    } catch (e: Exception) {
        handleCaughtException(e)
    }
}

inline fun <reified R : Any> RoutingContext.usePrincipalOrNull(
    block: (uid: PrimaryKey?) -> Result<R?>?,
) = block(call.principal<CustomPrincipal>()?.uid)

suspend fun RoutingContext.respondError(e: Throwable, buildType: String) {
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
                if (buildType == "prod") "" else (e.message ?: e.toString())
            )
        }
    }
}

inline fun <reified R : Any> DefaultWebSocketServerSession.usePrincipal(block: (PrimaryKey) -> R?) {
    val uid = call.principal<CustomPrincipal>()?.uid
    if (uid == null) {
        return
    } else {
        block(uid)
    }
}

inline fun <reified R> handleResult(backend: Backend): suspend RoutingContext.(it: Result<R>) -> Unit {
    return { result ->
        handleResultInternal(result, backend.customConfig.buildType)
    }
}

suspend inline fun <reified R> RoutingContext.handleResultInternal(it: Result<R>, buildType: String) {
    it.onSuccess {
        when (it) {
            null -> call.respond(HttpStatusCode.NotFound)
            is FileResponse -> {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        it.file.name
                    ).toString()
                )
                call.respondFile(it.file)
            }

            is PathResponse -> {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, it.file.name)
                        .toString()
                )
                call.respondPath(it.file)
            }

            is Unit -> call.respond(HttpStatusCode.OK)
            else -> call.respond(it)
        }
    }.onFailure { throwable ->
        respondError(throwable, buildType)
        if (throwable !is UnauthorizedException) {
            Sentry.withIsolationScope { scope ->
                scope.request = Request().apply {
                    url = call.request.uri
                    headers = call.request.headers.toMap().mapValues {
                        it.value.joinToString(",")
                    }
                    queryString = call.request.queryString()
                }
                Sentry.captureException(throwable)
            }
        }
        call.application.log.error("Occur server exception $", throwable)
    }
}
