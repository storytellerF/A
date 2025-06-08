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
    val acg: Long,
    val aid: String?,
    val nickname: String,
    val avatar: MediaInfo?,
) : Identifiable {
    override val objectType: ObjectType
        get() = ObjectType.USER

    companion object {
        val EMPTY = UserInfo(DEFAULT_PRIMARY_KEY, "", 0, "", "", null)
    }
}

enum class UserLogType {
    SIGN_IN, SIGN_UP, CREATE, DELETE, UPDATE, JOIN, EXIT
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
