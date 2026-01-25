package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class User(
    val aid: String?,
    val encryptionPublicKey: String?,
    val encryptionPrivateKey: String?,
    val publicKey: String,
    val address: String,
    val icon: PrimaryKey?,
    val nickname: String,
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val acgAmount: Long,
    val passType: PassType,
    val algoType: AlgoType,
    val notificationId: PrimaryKey,
) {
    companion object
}

fun User.toUserInfo(): UserInfo {
    return UserInfo(
        id,
        address,
        aid,
        nickname,
        null,
        encryptionPublicKey,
        encryptionPrivateKey,
        null,
        null
    )
}

data class RawUser(
    val user: User,
    val favoriteId: PrimaryKey? = null,
    val subscriptionId: PrimaryKey? = null,
)

fun RawUser.toUserInfo() = user.toUserInfo().copy(
    favoriteId = favoriteId,
    subscriptionId = subscriptionId
)
