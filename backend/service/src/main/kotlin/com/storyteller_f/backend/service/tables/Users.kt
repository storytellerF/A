package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.BaseEntity
import com.storyteller_f.backend.service.BaseTable
import com.storyteller_f.backend.service.customPrimaryKey
import com.storyteller_f.backend.service.userAddress
import com.storyteller_f.backend.service.userIcon
import com.storyteller_f.backend.service.userName
import com.storyteller_f.backend.service.userPublicKey
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.AlgoType
import com.storyteller_f.shared.type.PassType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Users : BaseTable() {
    val publicKey = userPublicKey()
    val address = userAddress()
    val icon = customPrimaryKey("icon")
    val nickname = userName()
    val acgAmount = long("acg_amount").default(0)
    val passType = enumerationByName<PassType>("pass_type", 20).default(PassType.RAW)
    val algoType = enumerationByName<AlgoType>("algo_type", 20).default(AlgoType.P256)
}

class User(
    val aid: String?,
    val publicKey: String,
    val address: String,
    val icon: PrimaryKey?,
    val nickname: String,
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val acgAmount: Long,
    val passType: PassType,
    val algoType: AlgoType,
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): User {
            return with(Users) {
                User(
                    row[Aids.value],
                    row[publicKey],
                    row[address],
                    row[icon],
                    row[nickname],
                    row[id],
                    row[createdTime],
                    row[acgAmount],
                    row[passType],
                    row[algoType]
                )
            }
        }

        fun find(function: SqlExpressionBuilder.() -> Op<Boolean>): Query {
            return Users.selectAll().where(function)
        }
    }
}

fun mapUserInfo(it: ResultRow): UserRawResult {
    return UserRawResult(User.wrapRow(it))
}

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

data class UserRawResult(val user: User)

object AlternateAccounts : Table() {
    val uid = customPrimaryKey("uid")
    val hostId = customPrimaryKey("host_id")
    override val primaryKey: PrimaryKey = PrimaryKey(uid)

    init {
        index("alternate-accounts-main", false, hostId, uid)
    }
}

class AlternateAccount(val uid: PrimaryKey, val hostId: PrimaryKey) {
    companion object {
        fun wrapRow(row: ResultRow): AlternateAccount {
            return with(AlternateAccounts) {
                AlternateAccount(
                    row[uid],
                    row[hostId]
                )
            }
        }
    }
}
