@file:Suppress("detekt.formatting")

package com.storyteller_f.a.api.server

import com.storyteller_f.a.api.core.SafeApi
import com.storyteller_f.a.api.core.SafeApiWithPath
import com.storyteller_f.a.api.core.SafeApiWithQuery
import com.storyteller_f.a.api.core.SafeApiWithQueryAndPath
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any, Q : Any, P : Any> SafeApiWithQueryAndPath<R, Q, P>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.(Q, P) -> Result<R?>?
) {
    route.get(urlString) {
        val q = getQuery(queryClass)
        val p = getPathQuery(pathClass)
        handleRequest<R, Q>(handleResult) {
            block(q, p)
        }
    }
}

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any, Q : Any> SafeApiWithQuery<R, Q>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.(Q) -> Result<R?>?
) {
    route.get(urlString) {
        val q = getQuery(queryClass)
        handleRequest<R, Q>(handleResult) {
            block(q)
        }
    }
}

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any, P : Any> SafeApiWithPath<R, P>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.(P) -> Result<R?>?
) {
    route.get(urlString) {
        val p = getPathQuery(pathClass)
        handleRequest<R, P>(handleResult) {
            block(p)
        }
    }
}

context(route: Route)
@OptIn(InternalSerializationApi::class)
operator fun <R : Any> SafeApi<R>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.() -> Result<R?>?
) {
    route.get(urlString) {
        handleRequest<R, Unit>(handleResult, block)
    }
}

private suspend fun <R : Any, Q : Any> RoutingContext.handleRequest(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.() -> Result<R?>?
) {
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

@OptIn(InternalSerializationApi::class)
private fun <Q : Any> RoutingContext.getQuery(kClass: KClass<Q>): Q {
    val querySerializer = kClass.serializer()
    return querySerializer.deserialize(
        ParametersDecoder(
            serializersModuleOf(kClass, querySerializer),
            call.queryParameters,
            querySerializer.descriptor.elementNames
        )
    )
}


@OptIn(InternalSerializationApi::class)
private fun <P : Any> RoutingContext.getPathQuery(kClass: KClass<P>): P {
    val pathSerializer = kClass.serializer()
    return pathSerializer.deserialize(
        ParametersDecoder(
            serializersModuleOf(kClass, pathSerializer),
            call.pathParameters,
            pathSerializer.descriptor.elementNames
        )
    )
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


