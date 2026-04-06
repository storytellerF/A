package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TitleWorkStatus
import com.storyteller_f.shared.type.ObjectStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime

class Title(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val name: String,
    val creator: PrimaryKey,
    val receiver: PrimaryKey,
    val type: TitleType,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val titleStatus: TitleWorkStatus,
    val descriptionTopicId: PrimaryKey,
    val expiresAt: LocalDateTime? = null,
    val status: ObjectStatus = ObjectStatus.NORMAL,
) {
    val readOnly get() = status == ObjectStatus.READ_ONLY
    companion object
}

fun Title.isExpired(currentTime: LocalDateTime = now()): Boolean {
    return titleStatus == TitleWorkStatus.EXPIRED || (expiresAt?.let { it <= currentTime } == true)
}

fun Title.effectiveTitleStatus(currentTime: LocalDateTime = now()): TitleWorkStatus {
    return if (isExpired(currentTime)) {
        TitleWorkStatus.EXPIRED
    } else {
        TitleWorkStatus.OK
    }
}

fun Title.toTitleInfo(
    extensions: TitleInfo.Extension? = null,
    favoriteId: PrimaryKey? = null,
    subscriptionId: PrimaryKey? = null,
    currentTime: LocalDateTime = now()
): TitleInfo {
    return TitleInfo(
        id = id,
        createdTime = createdTime,
        type = type,
        creator = creator,
        receiver = receiver,
        scopeId = scopeId,
        scopeType = scopeType,
        name = name,
        descriptionTopicId = descriptionTopicId,
        extension = extensions,
        favoriteId = favoriteId,
        subscriptionId = subscriptionId,
        titleStatus = effectiveTitleStatus(currentTime),
        expiresAt = expiresAt,
        status = status,
    )
}

data class RawTitle(
    val title: Title,
    val favoriteId: PrimaryKey? = null,
    val subscriptionId: PrimaryKey? = null
)
