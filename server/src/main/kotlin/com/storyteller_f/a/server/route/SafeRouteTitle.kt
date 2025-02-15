package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.service.createTitle
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.TitleType
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route

val titleMap = mutableMapOf<ObjectType, List<TitleType>>(
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

fun Route.bindProtectedTitleRoute(backend: Backend, reader: DatabaseReader) {
    post<RouteTitles> {
        val title = call.receive<NewTitle>()
        usePrincipal(reader) { uid ->
            val supportType = titleMap[title.scopeType]
            if (supportType != null) {
                if (supportType.contains(title.type)) {
                    createTitle(title, uid, backend)
                } else {
                    Result.failure(ForbiddenException("unsupported title type ${title.type} in ${title.scopeType}"))
                }
            } else {
                Result.success(null)
            }
        }
    }
}
