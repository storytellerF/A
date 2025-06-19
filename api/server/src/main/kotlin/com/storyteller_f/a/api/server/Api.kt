@file:Suppress("detekt.formatting")

package com.storyteller_f.a.api.server

import com.storyteller_f.a.api.core.ApiGet
import com.storyteller_f.a.api.core.ApiGetWithPath
import com.storyteller_f.a.api.core.ApiGetWithQuery
import com.storyteller_f.a.api.core.ApiGetWithQueryAndPath
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any, Q : Any, P : Any> ApiGetWithQueryAndPath<R, Q, P>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.(Q, P) -> Result<R?>?
) {
    route.get(urlString) {
        val querySerializer = queryClass.serializer()
        val q = querySerializer.deserialize(
            ParametersDecoder(
                serializersModuleOf(queryClass, querySerializer),
                call.queryParameters,
                querySerializer.descriptor.elementNames
            )
        )
        val pathSerializer = pathClass.serializer()
        val p = pathSerializer.deserialize(
            ParametersDecoder(
                serializersModuleOf(pathClass, pathSerializer),
                call.pathParameters,
                pathSerializer.descriptor.elementNames
            )
        )
        try {
            val result = block(q, p)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                handleResult(result)
            }
        } catch (e: Exception) {
            handleCaughtException(e)
        }
    }
}

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any, Q : Any> ApiGetWithQuery<R, Q>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.(Q) -> Result<R?>?
) {
    route.get(urlString) {
        val querySerializer = queryClass.serializer()
        val q = querySerializer.deserialize(
            ParametersDecoder(
                serializersModuleOf(queryClass, querySerializer),
                call.queryParameters,
                querySerializer.descriptor.elementNames
            )
        )
        try {
            val result = block(q)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                handleResult(result)
            }
        } catch (e: Exception) {
            handleCaughtException(e)
        }
    }
}

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any, P : Any> ApiGetWithPath<R, P>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.(P) -> Result<R?>?
) {
    route.get(urlString) {
        val pathSerializer = pathClass.serializer()
        val p = pathSerializer.deserialize(
            ParametersDecoder(
                serializersModuleOf(pathClass, pathSerializer),
                call.pathParameters,
                pathSerializer.descriptor.elementNames
            )
        )
        try {
            val result = block(p)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                handleResult(result)
            }
        } catch (e: Exception) {
            handleCaughtException(e)
        }
    }
}

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any> ApiGet<R>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.() -> Result<R?>?
) {
    route.get(urlString) {
        try {
            val result = block()
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                handleResult(result)
            }
        } catch (e: Exception) {
            handleCaughtException(e)
        }
    }
}

suspend fun RoutingContext.handleCaughtException(e: Exception) {
    call.application.log.error("Catch exception in api", e)
    if (!call.isHandled) {
        try {
            call.respond(HttpStatusCode.InternalServerError, "Catch exception")
        } catch (e: Exception) {
            call.application.log.error("Throw exception again when response internal server error", e)
        }
    }
}


