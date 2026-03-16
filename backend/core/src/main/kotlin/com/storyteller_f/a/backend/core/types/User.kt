package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UserStatus
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
    val status: UserStatus = UserStatus.NORMAL,
) {
    companion object
}

fun User.toUserInfo(avatar: FileInfo? = null): UserInfo {
    return UserInfo(
        id,
        address,
        aid,
        nickname,
        avatar,
        encryptionPublicKey,
        encryptionPrivateKey,
        null,
        null,
        status,
    )
}

data class RawUser(
    val user: User,
    val favoriteId: PrimaryKey? = null,
    val subscriptionId: PrimaryKey? = null,
)

fun RawUser.toUserInfo(avatar: FileInfo? = null) = user.toUserInfo(avatar).copy(
    favoriteId = favoriteId,
    subscriptionId = subscriptionId
)
