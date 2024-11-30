package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.RouteTopics
import com.storyteller_f.a.server.service.addTopicAtCommunity
import com.storyteller_f.a.server.service.getTopic
import com.storyteller_f.a.server.service.getTopicSnapshot
import com.storyteller_f.a.server.service.getTopics
import com.storyteller_f.a.server.service.searchPublicTopics
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeTopicRoute(backend: Backend) {
    get<RouteTopics.Search> {
        usePrincipalOrNull { id ->
            pagination<TopicInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchPublicTopics(n, s, it, backend)
            }
        }
    }

    get<RouteTopics.Id> {
        usePrincipalOrNull { id ->
            getTopic(it.id, id, backend)
        }
    }

    get<RouteTopics.Id.Topics> {
        usePrincipalOrNull { uid ->
            pagination<TopicInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                getTopics(it.parent.id, ObjectType.TOPIC, uid, backend, p, n, s)
            }
        }
    }
}

fun Route.bindProtectedSafeTopicRoute(backend: Backend) {
    get<RouteTopics.Id.Snapshot> {
        usePrincipal { id ->
            getTopicSnapshot(id, it.parent.id, backend)
        }
    }

    post<RouteTopics> {
        usePrincipal {
            addTopicAtCommunity(it, backend)
        }
    }
}
