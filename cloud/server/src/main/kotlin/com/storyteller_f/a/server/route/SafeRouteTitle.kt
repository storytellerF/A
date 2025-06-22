package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.api.server.receiveBody
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.service.createTitle
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

val titleMap = mutableMapOf(
    ObjectType.COMMUNITY to listOf(
        TitleType.REGULAR,
        TitleType.JOIN
    ),
    ObjectType.ROOM to listOf(
        TitleType.REGULAR,
        TitleType.JOIN
    ),
    ObjectType.USER to listOf(
        TitleType.REGULAR
    ),
    ObjectType.TOPIC to listOf(
        TitleType.REGULAR
    )
)

fun Route.bindProtectedTitleRoute(backend: Backend) {
    CustomApi.Titles.add.invoke(RoutingContext::handleResult) {
        val title = with(it) { receiveBody() }
        usePrincipal { uid ->
            val supportType = titleMap[title.scopeType]
            if (supportType != null) {
                if (supportType.contains(title.type)) {
                    backend.createTitle(title, uid)
                } else {
                    Result.failure(ForbiddenException("unsupported title type ${title.type} in ${title.scopeType}"))
                }
            } else {
                Result.success(null)
            }
        }
    }
}
