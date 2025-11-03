package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.AdminApi
import com.storyteller_f.a.api.NewUser
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.api.SignInBody
import com.storyteller_f.a.api.SignUpBody
import com.storyteller_f.route4k.ktor.client.invoke
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun PanelSessionManager.getAllUsers(query: PaginationQuery) = serviceCatching {
    AdminApi.Users.get(query)
}

suspend fun PanelSessionManager.signUp(newUser: SignUpBody) = serviceCatching {
    AdminApi.signUp(newUser) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun PanelSessionManager.signIn(signInPack: SignInBody) = serviceCatching {
    AdminApi.signIn(signInPack) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun PanelSessionManager.signOut() = serviceCatching {
    AdminApi.signOut(Unit) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun PanelSessionManager.getData() = serviceCatching {
    AdminApi.getData()
}

suspend fun PanelSessionManager.overview() = serviceCatching {
    AdminApi.overview()
}

suspend fun PanelSessionManager.addUser(newUser: NewUser) = serviceCatching {
    AdminApi.Users.add(newUser) {
        contentType(ContentType.Application.Json)
    }
}
