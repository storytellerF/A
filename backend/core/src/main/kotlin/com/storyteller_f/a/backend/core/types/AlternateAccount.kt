package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.PrimaryKey

class AlternateAccount(val uid: PrimaryKey, val privateKey: String, val hostId: PrimaryKey, val remark: String?)
