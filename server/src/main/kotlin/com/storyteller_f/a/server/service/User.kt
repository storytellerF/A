package com.storyteller_f.a.server.service

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.Users
import com.storyteller_f.tables.getUserAid
import io.ktor.resources.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.update

@Resource("/users")
class RouteUsers(val aid: String? = null) {
    @Resource("{id}")
    class Id(@Suppress("unused") val parent: RouteUsers = RouteUsers(), val id: PrimaryKey)

    @Resource("update")
    class Update(@Suppress("unused") val parent: RouteUsers = RouteUsers())

    @Resource("search")
    class Search(@Suppress("unused") val parent: RouteUsers, val word: String? = null)
}

suspend fun RoutingContext.updateUser(id: PrimaryKey): Result<Int> {
    val newUser = call.receive<UserInfo>()
    val result1 = listOf(suspend {
        if (!newUser.aid.isNullOrBlank()) {
            // check aid is null
            getUserAid(id).mapResult {
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
