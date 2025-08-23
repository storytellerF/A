package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class User(
    val aid: String?,
    val publicKey: String,
    val address: String,
    val icon: PrimaryKey?,
    val nickname: String,
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val acgAmount: Long,
    val passType: PassType,
    val algoType: AlgoType,
) {
    companion object
}

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

data class RawUser(val user: User)
