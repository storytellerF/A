package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    override val id: PrimaryKey,
    val address: String,
    val aid: String?,
    val nickname: String,
    val avatar: FileInfo?,
) : PrimaryKeyIdentifiable {
    override val objectType: ObjectType
        get() = ObjectType.USER

    companion object {
        val EMPTY = UserInfo(DEFAULT_PRIMARY_KEY, "", "", "", null)
    }
}

@Serializable
data class UserOverview(
    val subscriptionCount: Long,
    val favoriteCount: Long,
    val acg: Long,
    val childAccountCount: Long
)

enum class UserLogType {
    SIGN_IN, SIGN_UP, CREATE, DELETE, UPDATE, JOIN, EXIT,
    ADD_ALTERNATIVE_ACCOUNT, REMOVE_ALTERNATIVE_ACCOUNT,
    ADD_FAVORITE, REMOVE_FAVORITE,
    ADD_SUBSCRIPTION, REMOVE_SUBSCRIPTION
}

@Serializable
data class UserLogInfo(
    val id: PrimaryKey,
    val uid: PrimaryKey,
    val type: UserLogType,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val createdTime: LocalDate
)

@Serializable
data class UserPubKeyInfo(val id: PrimaryKey, val pubKey: String)

enum class AlgoType {
    P256,
    DILITHIUM
}

enum class PassType {
    RAW, ANDROID, BIT_WARDEN
}

@Serializable
data class ChildAccountInfo(
    val hostId: PrimaryKey,
    val privateKey: String,
    val userInfo: UserInfo
) : PrimaryKeyIdentifiable {
    override val id: PrimaryKey
        get() = userInfo.id
    override val objectType: ObjectType
        get() = ObjectType.USER
}

enum class QuotaType {
    FILE
}

@Serializable
data class QuotaInfo(
    val ownerId: PrimaryKey,
    val ownerType: ObjectType,
    val quotaType: QuotaType,
    val total: Long,
    val used: Long,
    val lockId: PrimaryKey?
)
