package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.ADDRESS_LENGTH
import com.storyteller_f.a.backend.core.PUBLIC_KEY_LENGTH
import com.storyteller_f.a.backend.core.USER_NICKNAME
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import org.jetbrains.exposed.v1.core.ResultRow

object PanelAccounts : BaseTable() {
    val name = varchar("name", 100)
    val publicKey = varchar("public_key", PUBLIC_KEY_LENGTH).uniqueIndex()
    val address = varchar("address", ADDRESS_LENGTH).uniqueIndex()
    val passType = enumerationByName<PassType>("pass_type", 20).default(PassType.RAW)
    val algoType = enumerationByName<AlgoType>("algo_type", 20).default(AlgoType.P256)
}

fun PanelAccount.Companion.wrapRow(resultRow: ResultRow): PanelAccount {
    return PanelAccount(
        resultRow[PanelAccounts.id],
        resultRow[PanelAccounts.name],
        resultRow[PanelAccounts.passType],
        resultRow[PanelAccounts.algoType],
        resultRow[PanelAccounts.publicKey],
        resultRow[PanelAccounts.address],
    )
}
