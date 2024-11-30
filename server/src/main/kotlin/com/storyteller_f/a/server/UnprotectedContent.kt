package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.searchPublicTopics
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.server.routing.*

fun Route.unProtectedContent(backend: Backend) {
    bindPublicRoute(backend)
    bindSafeRoomRoute(backend)
    bindSafeTopicRoute(backend)
    bindSafeCommunityRoute(backend)
    bindSafeUserRoute(backend)
    bindProtectedSafeUserRoute()
}

private fun Route.bindPublicRoute(backend: Backend) {
    get("/world") {
        omitPrincipal {
            pagination<TopicInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { prePageToken, nextPageToken, size ->
                searchPublicTopics(backend, prePageToken, nextPageToken, size)
            }
        }
    }

}
