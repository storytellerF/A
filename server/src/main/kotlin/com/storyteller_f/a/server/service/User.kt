package com.storyteller_f.a.server.service

import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.getUserAid
import com.storyteller_f.tables.updateUser
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

suspend fun RoutingContext.updateUser(id: PrimaryKey): Result<UserInfo?> {
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
        if (result.isSuccess) {
            null
        } else {
            result
        }
    }
    if (result1 != null) return result1.map { null }
    return updateUser(id, newUser).map {
        if (it) {
            newUser
        } else {
            null
        }
    }
}
