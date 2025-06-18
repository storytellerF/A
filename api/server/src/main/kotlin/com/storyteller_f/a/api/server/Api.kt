@file:Suppress("detekt.formatting")

package com.storyteller_f.a.api.server

import com.storyteller_f.a.api.core.ApiGet
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
operator fun <Q : Any, R : Any> ApiGet<Q, R>.invoke(
    handleResult: suspend RoutingContext.(Result<R?>) -> Unit,
    block: suspend RoutingContext.(Q) -> Result<R?>?
) {
    route.get(path) {
        val serializer = queryClass.serializer()
        val decoder =
            ParametersDecoder(
                serializersModuleOf(queryClass, serializer),
                call.queryParameters,
                serializer.descriptor.elementNames
            )
        val q = serializer.deserialize(decoder)
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


