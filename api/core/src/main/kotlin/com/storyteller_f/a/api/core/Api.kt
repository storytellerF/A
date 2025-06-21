package com.storyteller_f.a.api.core

import kotlin.reflect.KClass

enum class MutationMethodType {
    POST, PUT, PATCH, DELETE
}

enum class SafeMethodType {
    GET, OPTIONS
}

sealed interface AbstractApi<Resp> {
    val urlString: String
}

interface AbstractMutationApi<Resp, Body> : AbstractApi<Resp> {
    val methodType: MutationMethodType
}

interface AbstractSafeApi<Resp> : AbstractApi<Resp> {
    val methodType: SafeMethodType
}

interface WithQueryApi<T : Any> {
    val queryClass: KClass<T>
}

class SafeApi<Resp : Any>(
    override val urlString: String,
    override val methodType: SafeMethodType,
) : AbstractSafeApi<Resp>

class SafeApiWithQuery<Resp : Any, Query : Any>(
    override val urlString: String,
    override val queryClass: KClass<Query>,
    override val methodType: SafeMethodType,
) : AbstractSafeApi<Resp>, WithQueryApi<Query>

class SafeApiWithPath<Resp : Any, PathQuery : Any>(
    override val urlString: String,
    val pathClass: KClass<PathQuery>,
    override val methodType: SafeMethodType
) : AbstractSafeApi<Resp>

class SafeApiWithQueryAndPath<Resp : Any, Query : Any, PathQuery : Any>(
    override val urlString: String,
    override val queryClass: KClass<Query>,
    val pathClass: KClass<PathQuery>,
    override val methodType: SafeMethodType
) : AbstractSafeApi<Resp>, WithQueryApi<Query>

class MutationApi<Resp : Any, Body : Any>(
    override val urlString: String,
    override val methodType: MutationMethodType,
) : AbstractMutationApi<Resp, Body>

class MutationApiWithQuery<Resp : Any, Body : Any, Query : Any>(
    override val urlString: String,
    override val queryClass: KClass<Query>,
    override val methodType: MutationMethodType,
) : AbstractMutationApi<Resp, Body>, WithQueryApi<Query>

class MutationApiWithPath<Resp : Any, Body : Any, PathQuery : Any>(
    override val urlString: String,
    val pathClass: KClass<PathQuery>,
    override val methodType: MutationMethodType
) : AbstractMutationApi<Resp, Body>

class MutationApiWithQueryAndPath<Resp : Any, Body : Any, Query : Any, PathQuery : Any>(
    override val urlString: String,
    override val queryClass: KClass<Query>,
    val pathClass: KClass<PathQuery>,
    override val methodType: MutationMethodType
) : AbstractMutationApi<Resp, Body>, WithQueryApi<Query>

inline fun <Resp : Any, reified Query : Any, reified PathQuery : Any> safeApiWithQueryAndPath(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApiWithQueryAndPath<Resp, Query, PathQuery> {
    return SafeApiWithQueryAndPath(
        path,
        Query::class,
        PathQuery::class,
        methodType
    )
}

inline fun <Resp : Any, reified Query : Any> safeApiWithQuery(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApiWithQuery<Resp, Query> {
    return SafeApiWithQuery(
        path,
        Query::class,
        methodType
    )
}

inline fun <Resp : Any, reified Path : Any> safeApiWithPath(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApiWithPath<Resp, Path> = SafeApiWithPath(
    path,
    Path::class,
    methodType
)

fun <Resp : Any> safeApi(
    path: String,
    methodType: SafeMethodType = SafeMethodType.GET
): SafeApi<Resp> {
    return SafeApi(
        path,
        methodType
    )
}

inline fun <Resp : Any, Body : Any, reified Query : Any, reified PathQuery : Any> mutationApiWithQueryAndPath(
    path: String,
    methodType: MutationMethodType = MutationMethodType.POST
): MutationApiWithQueryAndPath<Resp, Body, Query, PathQuery> {
    return MutationApiWithQueryAndPath(
        path,
        Query::class,
        PathQuery::class,
        methodType
    )
}

inline fun <Resp : Any, Body : Any, reified Query : Any> mutationApiWithQuery(
    path: String,
    methodType: MutationMethodType = MutationMethodType.POST
): MutationApiWithQuery<Resp, Body, Query> {
    return MutationApiWithQuery(
        path,
        Query::class,
        methodType
    )
}

inline fun <Resp : Any, Body : Any, reified Path : Any> mutationApiWithPath(
    path: String,
    methodType: MutationMethodType = MutationMethodType.POST
): MutationApiWithPath<Resp, Body, Path> = MutationApiWithPath(
    path,
    Path::class,
    methodType
)

fun <Resp : Any, Body : Any> mutationApi(
    path: String,
    methodType: MutationMethodType = MutationMethodType.POST
): MutationApi<Resp, Body> {
    return MutationApi(
        path,
        methodType
    )
}
