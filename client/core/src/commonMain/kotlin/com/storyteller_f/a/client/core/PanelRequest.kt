package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.core.AdminApi
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.route4k.ktor.client.invoke
import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun PanelSessionManager.getAllUsers(query: PaginationQuery) = serviceCatching {
    AdminApi.Users.get.invoke(query)
}

suspend fun PanelSessionManager.signUp(signUpPack: SignUpPack) = serviceCatching {
    AdminApi.signUp.invoke(signUpPack) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun PanelSessionManager.signIn(signInPack: SignInPack) = serviceCatching {
    AdminApi.signIn.invoke(signInPack) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun PanelSessionManager.signOut() = serviceCatching {
    AdminApi.signOut.invoke(Unit) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun PanelSessionManager.getData() = serviceCatching {
    AdminApi.getData.invoke()
}
