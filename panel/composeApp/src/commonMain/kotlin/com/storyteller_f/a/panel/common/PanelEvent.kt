package com.storyteller_f.a.panel.common

import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UserStatus

data class OnUserAdded(val info: UserInfo)

data class OnUserStatusUpdated(val uid: PrimaryKey, val status: UserStatus)
