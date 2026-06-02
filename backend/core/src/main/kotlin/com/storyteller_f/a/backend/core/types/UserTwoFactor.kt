package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.TwoFactorSettingsInfo
import com.storyteller_f.shared.model.TwoFactorType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

data class UserTwoFactor(
    val uid: PrimaryKey,
    val enabled: Boolean,
    val type: TwoFactorType,
    val totpSecret: String,
    val recoveryCodeHashes: List<String>,
    val updatedAt: LocalDateTime,
) {
    companion object
}

fun UserTwoFactor?.toTwoFactorSettingsInfo(): TwoFactorSettingsInfo {
    return TwoFactorSettingsInfo(
        enabled = this?.enabled == true,
        type = this?.type?.takeIf { enabled },
    )
}
