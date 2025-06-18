package com.storyteller_f.a.api.core

import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PosterSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object Api {
    object Topics {
        object Aid {
            val get = ApiGet("topics/aid", AidQuery::class, serializer<TopicInfo>())
        }
    }

    object Root {
        val get = ApiGet("/", Unit::class, serializer<String>())
    }

    object Communities {
        object Search {
            @Serializable
            data class Query(
                val joinStatus: JoinStatusSearch? = null,
                val word: String? = null,
                val target: PrimaryKey? = null,
                val hasPoster: PosterSearch? = null,
                val nextPageToken: String? = null,
                val size: Int = 10,
            )

            val getting = ApiGet(
                "communities/search",
                Query::class,
                serializer<ServerResponse<CommunityInfo>>()
            )
        }
    }
}

class AidQuery(val aid: String)

class ApiGet<Query : Any, Resp : Any>(
    val path: String,
    val queryClass: KClass<Query>,
    val respSerializer: KSerializer<Resp>
)

class ApiPost<Body : Any, Resp : Any>(val path: String, val bodyClass: KClass<Body>, val respClass: KClass<Resp>)
