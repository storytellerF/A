package com.storyteller_f.a.server.common

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.util.*


inline fun <reified T : Any, reified R : Any> RoutingContext.checkParameter(
    name: String = "id",
    block: (T) -> Result<R?>
): Result<R?> {
    return call.parameters.checkParameter<R, T>(name, block)
}

inline fun <reified R : Any, reified T : Any> Parameters.checkParameter(
    name: String,
    block: (T) -> Result<R?>
): Result<R?> {
    val value = if (T::class == ULong::class) {
        getOrFail<String>(name).toULong() as T
    } else {
        getOrFail<T>(name)
    }
    return block(value)
}

inline fun <reified T : Any, reified R : Any> RoutingContext.checkQueryParameter(
    name: String = "id",
    block: (T) -> Result<R?>
): Result<R?> {
    return call.request.queryParameters.checkParameter<R, T>(name, block)
}
