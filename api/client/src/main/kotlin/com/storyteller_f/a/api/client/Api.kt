@file:Suppress("detekt.formatting")
package com.storyteller_f.a.api.client

import com.storyteller_f.a.api.core.ApiGet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.internal.NamedValueEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

context(route: HttpClient)
@OptIn(InternalSerializationApi::class)
suspend inline operator fun <Q : Any, reified R : Any> ApiGet<Q, R>.invoke(query: Q): R {
    val params = encodeQueryParams(query, queryClass, queryClass.serializer())
    return route.get(path) {
        url {
            params.forEach { (key, value) ->
                parameters.append(key, value)
            }
        }
    }.body<R>()
}

