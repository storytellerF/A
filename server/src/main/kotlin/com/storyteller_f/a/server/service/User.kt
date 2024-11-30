package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.tables.User
import com.storyteller_f.tables.Users
import io.ktor.resources.Resource
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.update

@Resource("/users")
class RouteUsers(val aid: String? = null) {
    @Resource("{id}")
    class Id(val parent: RouteUsers = RouteUsers(), val id: PrimaryKey)

    @Resource("update")
    class Update(val parent: RouteUsers = RouteUsers())

    @Resource("search")
    class Search(val parent: RouteUsers, val word: String? = null)
}

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

fun toFinalUserInfo(p: Pair<UserInfo, String?>, backend: Backend): Result<UserInfo> {
    val (userInfo, icon) = p
    return backend.mediaService.get("apic", listOf(icon)).map { value ->
        userInfo.copy(avatar = value.firstOrNull()?.let {
            MediaInfo(it)
        })
    }
}

suspend fun RoutingContext.getUser(
    it: PrimaryKey,
    backend: Backend
) = DatabaseFactory.queryNotNull({
    toUserInfo() to icon
}) {
    User.findById(it)
}.mapResultNotNull {
    toFinalUserInfo(it, backend)
}

suspend fun RoutingContext.getUserByAid(
    aid: String,
    backend: Backend
) = DatabaseFactory.first({
    toUserInfo() to icon
}, User::wrapRow) {
    User.find {
        Users.aid eq aid
    }
}.mapResultNotNull {
    toFinalUserInfo(it, backend)
}

suspend fun RoutingContext.updateUser(id: PrimaryKey): Result<Int> {
    val newUser = call.receive<UserInfo>()
    val result1 = listOf(suspend {
        if (!newUser.aid.isNullOrBlank()) {
            // check aid is null
            DatabaseFactory.queryNotNull({
                aid
            }) {
                User.findById(id)
            }.mapResult {
                if (it != null) {
                    Result.failure(BadRequestException("aid is not null."))
                } else {
                    Result.success(true)
                }
            }
        } else {
            Result.success(true)
        }
    }).firstNotNullOfOrNull {
        val result = it()
        if (result.getOrNull() == true) {
            null
        } else {
            result
        }
    }
    if (result1 != null) return result1.map { 0 }
    return DatabaseFactory.dbQuery {
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
