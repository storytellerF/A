package com.storyteller_f.a.panel.common

import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UserStatus

data class OnUserAdded(val info: UserInfo)

data class OnUserStatusUpdated(val uid: PrimaryKey, val status: UserStatus)

data class OnCommunityStatusUpdated(val id: PrimaryKey, val readOnly: Boolean)

data class OnRoomStatusUpdated(val id: PrimaryKey, val readOnly: Boolean)

data class OnTopicStatusUpdated(val id: PrimaryKey, val readOnly: Boolean)

data class OnTitleStatusUpdated(val id: PrimaryKey, val readOnly: Boolean)

data class OnFileStatusUpdated(val id: PrimaryKey, val readOnly: Boolean)
