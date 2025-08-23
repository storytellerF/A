package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.PrimaryKey

class ChildAccount(val uid: PrimaryKey, val privateKey: String, val hostId: PrimaryKey, val remark: String?) {
    companion object
}

data class RawChildAccount(val childAccount: ChildAccount, val rawUser: RawUser)
