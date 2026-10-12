/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.endpoint4k.ktor.server.invoke
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.bindUnauthenticatedRoute(backend: Backend) {
    get("/ping") {
        call.respondText("pong")
    }

    CustomApi.Root.get(handleResult(backend)) {
        Result.success("${backend.customConfig.flavor} ${backend.customConfig.buildType}")
    }
}
