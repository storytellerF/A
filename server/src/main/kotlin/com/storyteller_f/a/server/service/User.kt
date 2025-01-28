package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.CustomBadRequestException
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.USER_NICKNAME
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.getUser
import com.storyteller_f.tables.getUserAid
import com.storyteller_f.tables.updateUser
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

suspend fun RoutingContext.updateUser(id: PrimaryKey, backend: Backend): Result<UserInfo?> {
    val old = call.receive<UserInfo>()
    val newUser = old.copy(nickname = old.nickname.trim(), aid = old.aid?.trim())
    val result1 = listOf(suspend {
        checkAidModifyTimes(newUser, id)
    }, suspend {
        checkAid(newUser)
    }, suspend {
        checkNickname(newUser)
    }, suspend {
        checkAvatar(newUser, backend)
    }).firstNotNullOfOrNull {
        val result = it()
        if (result.isSuccess) {
            null
        } else {
            result
        }
    }
    if (result1 != null) return result1.map { null }
    return DatabaseFactory.updateUser(id, newUser).mapResult {
        if (it) {
            DatabaseFactory.getUser(id, backend)
        } else {
            Result.success(null)
        }
    }
}

private suspend fun checkAidModifyTimes(
    newUser: UserInfo,
    id: PrimaryKey
) = if (newUser.aid.isNullOrBlank()) {
    Result.success(Unit)
} else {
    // check aid is null
    DatabaseFactory.getUserAid(id).mapResult {
        if (it != null) {
            Result.failure(BadRequestException("aid is not null."))
        } else {
            Result.success(Unit)
        }
    }
}

private fun checkAid(newUser: UserInfo): Result<Unit> {
    val aid = newUser.aid
    return when {
        aid.isNullOrBlank() -> Result.success(Unit)
        aid.length in 2..20 -> Result.success(Unit)
        !aid.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-'
        } -> {
            Result.failure(CustomBadRequestException("only support alphabet, number, underline and hyphen"))
        }
        else -> Result.failure(BadRequestException("aid too long"))
    }
}

private fun checkNickname(newUser: UserInfo): Result<Unit> {
    val nickname = newUser.nickname
    return when {
        nickname.isEmpty() -> Result.success(Unit)
        nickname.length in 2..USER_NICKNAME -> Result.success(Unit)
        else -> Result.failure(CustomBadRequestException("user nickname must be between in 2 and 20"))
    }
}

private suspend fun checkAvatar(
    newUser: UserInfo,
    backend: Backend
): Result<Unit> {
    val avatar = newUser.avatar?.item?.name
    return if (avatar != null) {
        backend.mediaService.get(AMEDIA_BUCKET, listOf(avatar)).mapResult {
            val mediaInfo = it.firstOrNull()
            when {
                mediaInfo == null -> Result.failure(CustomBadRequestException("avatar not valid"))
                mediaInfo.item.contentType.startsWith("image/") -> Result.success(Unit)
                else -> Result.failure(CustomBadRequestException("avatar must be image"))
            }
        }
    } else {
        Result.success(Unit)
    }
}
