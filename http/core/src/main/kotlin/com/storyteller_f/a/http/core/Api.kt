package com.storyteller_f.a.http.core

import kotlin.reflect.KClass

object Api {
    object Topics {
        object Aid {
            val get = ApiGet("topics/aid", AidQuery::class)
        }
    }
}

class AidQuery(val aid: String)

class ApiGet<SearchQuery : Any>(val path: String, val queryClass: KClass<SearchQuery>)