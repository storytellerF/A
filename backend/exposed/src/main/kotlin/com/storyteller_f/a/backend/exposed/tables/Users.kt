package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.ADDRESS_LENGTH
import com.storyteller_f.a.backend.core.PUBLIC_KEY_LENGTH
import com.storyteller_f.a.backend.core.USER_NICKNAME
import com.storyteller_f.a.backend.core.types.ChildAccount
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.selectAll

object Users : BaseTable() {
    val publicKey = varchar("public_key", PUBLIC_KEY_LENGTH).uniqueIndex()
    val address = varchar("address", ADDRESS_LENGTH).uniqueIndex()
    val icon = customPrimaryKey("icon").nullable()
    val nickname = varchar("nickname", USER_NICKNAME).index()
    val acgAmount = long("acg_amount").default(0)
    val passType = enumerationByName<PassType>("pass_type", 20).default(PassType.RAW)
    val algoType = enumerationByName<AlgoType>("algo_type", 20).default(AlgoType.P256)
}

fun User.Companion.wrapRow(row: ResultRow): User {
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

fun User.Companion.find(function: () -> Op<Boolean>): Query {
    return Users.selectAll().where(function)
}

fun mapUserInfo(it: ResultRow): RawUser {
    return RawUser(User.wrapRow(it))
}

object ChildAccounts : Table() {
    val uid = customPrimaryKey("uid")
    val hostId = customPrimaryKey("host_id")
    val privateKey = varchar("private_key", PUBLIC_KEY_LENGTH).uniqueIndex()
    val remark = text("remark").nullable()
    override val primaryKey: PrimaryKey = PrimaryKey(uid)

    init {
        index("child-accounts-main", false, hostId, uid)
    }
}

fun ChildAccount.Companion.wrapRow(row: ResultRow): ChildAccount {
    return with(ChildAccounts) {
        ChildAccount(
            row[uid],
            row[privateKey],
            row[hostId],
            row[remark]
        )
    }
}
