package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.User
import com.storyteller_f.tables.Users
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

fun toFinalUserInfo(p: Pair<UserInfo, String?>, backend: Backend): UserInfo {
    val (userInfo, icon) = p
    val avatar = backend.mediaService.get("apic", listOf(icon)).firstOrNull()?.let {
        MediaInfo(it)
    }
    return userInfo.copy(avatar = avatar)
}

suspend fun RoutingContext.getUser(
    it: PrimaryKey,
    backend: Backend
) = runCatching {
    DatabaseFactory.queryNotNull({
        toUserInfo() to icon
    }) {
        User.findById(it)
    }?.let { toFinalUserInfo(it, backend) }
}

suspend fun RoutingContext.getUserByAid(
    aid: String,
    backend: Backend
) = runCatching {
    DatabaseFactory.first({
        toUserInfo() to icon
    }, User::wrapRow) {
        User.find {
            Users.aid eq aid
        }
    }?.let { toFinalUserInfo(it, backend) }
}

suspend fun RoutingContext.updateUser(id: PrimaryKey) =
    runCatching {
        val newUser = call.receive<UserInfo>()
        if (!newUser.aid.isNullOrBlank()) {
            // check aid is null
            if (DatabaseFactory.queryNotNull({
                    aid
                }) {
                    User.findById(id)
                } != null) {
                throw BadRequestException("aid is not null.")
            }
        }
        DatabaseFactory.dbQuery {
            Users.update({
                Users.id eq id
            }) {
                if (newUser.nickname.isNotBlank()) {
                    it[nickname] = newUser.nickname
                }
                if (!newUser.aid.isNullOrBlank()) {
                    it[aid] = newUser.aid
                }
            }
        }
    }
