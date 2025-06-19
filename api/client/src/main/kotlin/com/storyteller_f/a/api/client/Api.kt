@file:Suppress("detekt.formatting")

package com.storyteller_f.a.api.client

import com.storyteller_f.a.api.core.ApiGet
import com.storyteller_f.a.api.core.ApiGetWithPath
import com.storyteller_f.a.api.core.ApiGetWithQuery
import com.storyteller_f.a.api.core.ApiGetWithQueryAndPath
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

context(route: HttpClient)
@OptIn(InternalSerializationApi::class)
suspend inline operator fun <Q : Any, reified R : Any> ApiGet<R>.invoke(query: Q): R {
    return route.get(urlString) {

    }.body<R>()
}

context(route: HttpClient)
@OptIn(InternalSerializationApi::class)
suspend inline operator fun <reified R : Any, Q : Any> ApiGetWithQuery<R, Q>.invoke(query: Q): R {
    val params = encodeQueryParams(query, queryClass, queryClass.serializer())
    return route.get(urlString) {
        url {
            params.forEach { (key, value) ->
                parameters.append(key, value)
            }
        }
    }.body<R>()
}


context(route: HttpClient)
@OptIn(InternalSerializationApi::class)
suspend inline operator fun <reified R : Any, Q : Any, P : Any> ApiGetWithQueryAndPath<R, Q, P>.invoke(
    query: Q,
    path: P
): R {
    val newUrlString = getUrlString<P, R>(path, pathClass, urlString)

    val params = encodeQueryParams(query, queryClass, queryClass.serializer())
    return route.get(newUrlString) {
        url {
            params.forEach { (key, value) ->
                parameters.append(key, value)
            }
        }
    }.body<R>()
}

context(route: HttpClient)
@OptIn(InternalSerializationApi::class)
suspend inline operator fun <reified R : Any, P : Any> ApiGetWithPath<R, P>.invoke(path: P): R {
    val newUrlString = getUrlString<P, R>(path, pathClass, urlString)
    return route.get(newUrlString) {

    }.body<R>()
}

@OptIn(InternalSerializationApi::class)
inline fun <P : Any, reified R : Any> getUrlString(path: P, pathClass: KClass<P>, urlString: String): String {
    val pathParams = encodeQueryParams(path, pathClass, pathClass.serializer())
    val newUrlString = pathParams.toList().fold(urlString) { acc, (key, value) ->
        acc.replace("{$key}", value)
    }
    return newUrlString
}

