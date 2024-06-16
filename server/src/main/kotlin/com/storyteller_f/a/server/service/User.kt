package com.storyteller_f.a.server.service

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.backend
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.tables.User
import io.ktor.server.routing.*

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

fun toFinalUserInfo(p: Pair<UserInfo, String?>): UserInfo {
    val (userInfo, icon) = p
    val avatar = backend.mediaService.get("apic", listOf(icon)).firstOrNull()?.let {
        MediaInfo(it)
    }
    return userInfo.copy(avatar = avatar)
}


suspend fun RoutingContext.getUser(
    it: OKey
) = runCatching {
    DatabaseFactory.queryNotNull({
        toUserInfo() to icon
    }) {
        User.findById(it)
    }?.let(::toFinalUserInfo)
}
