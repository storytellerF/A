package com.storyteller_f.a.api

import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface PageableQuery {
    val size: Int
    val nextPageToken: String?
    val prePageToken: String?
}

@Serializable
class PaginationQuery(
    override val nextPageToken: String? = null,
    override val prePageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE
) : PageableQuery

@Serializable
class SearchQuery(
    val word: String,
    override val nextPageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE,
    override val prePageToken: String? = null,
) : PageableQuery

@Serializable
class CommonPath(val id: PrimaryKey)

@Serializable
class NewSubscription(
    val objectId: PrimaryKey,
    val objectType: ObjectType
) {
    fun tuple() = ObjectTuple(objectId, objectType)
}

@Serializable
class NewCommunity(
    val name: String,
    val aid: String,
    val icon: PrimaryKey? = null,
    val memberPolicy: MemberPolicy = MemberPolicy.OPEN
)

@Serializable
class NewDevice(val endpointUrl: String)

@Serializable
class NewReaction(val emoji: String)

@Serializable
class DeleteReaction(val emoji: String)

@Serializable
class NewRoom(
    val name: String,
    val aid: String,
    val icon: PrimaryKey? = null,
    val communityId: PrimaryKey? = null
)

@Serializable
class NewTitle(
    val name: String,
    val type: TitleType,
    val receiver: PrimaryKey,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val description: String,
    val expiresAt: LocalDateTime? = null,
)

@Serializable
class NewTopic(val parentType: ObjectType, val parentId: PrimaryKey, val content: String) {
    val tuple = ObjectTuple(parentId, parentType)
}

@Serializable
class NewFavorite(val objectType: ObjectType, val objectId: PrimaryKey) {
    fun tuple(): ObjectTuple {
        return ObjectTuple(objectId, objectType)
    }
}

@Serializable
sealed class TransferAuthKey {
    abstract val algo: AlgoType
    abstract val derPublicKey: String

    @Serializable
    @SerialName("P256")
    data class P256(
        override val derPublicKey: String,
    ) : TransferAuthKey() {
        override val algo = AlgoType.P256
    }

    @Serializable
    @SerialName("Dilithium")
    data class Dilithium(
        override val derPublicKey: String,
        val derEncryptionPublicKey: String,
    ) : TransferAuthKey() {
        override val algo = AlgoType.DILITHIUM
    }
}

@Serializable
class NewUser(
    val nickname: String? = null,
    val aid: String? = null,
    val authKey: TransferAuthKey
)

@Serializable
class SignUpBody(
    val publicKey: String,
    val signature: String,
    val encryptionPublicKey: String? = null
)

@Serializable
class SignInBody(val address: String, val signature: String)

@Serializable
sealed class SignInResponse {
    @Serializable
    @SerialName("success")
    data class Success(val userInfo: UserInfo) : SignInResponse()

    @Serializable
    @SerialName("requires_totp")
    data object RequiresTotp : SignInResponse()
}

@Serializable
class TotpCodeBody(val code: String)

@Serializable
class TopicQuery(
    val pinType: TopicPinSearch? = null,
    val fillHasCommented: Boolean? = null,
    override val prePageToken: String? = null,
    override val nextPageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE,
) : PageableQuery {
    constructor(
        pinType: TopicPinSearch? = null,
        fillHasCommented: Boolean? = null,
        paginationQuery: PaginationQuery,
    ) : this(
        pinType,
        fillHasCommented,
        paginationQuery.prePageToken,
        paginationQuery.nextPageToken,
        paginationQuery.size
    )
}

@Serializable
class PanelLogsQuery(
    val targetId: PrimaryKey,
    val objectType: ObjectType,
    override val nextPageToken: String? = null,
    override val prePageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE,
) : PageableQuery {
    init {
        require(size in 1..MAX_PAGE_SIZE) {
            "Page size must be between 1 and $MAX_PAGE_SIZE"
        }
    }
}

@Serializable
class TaskRecordsQuery(
    val type: TaskRecordType? = null,
    override val nextPageToken: String? = null,
    override val prePageToken: String? = null,
    override val size: Int = DEFAULT_PAGE_SIZE,
) : PageableQuery {
    init {
        require(size in 1..MAX_PAGE_SIZE) {
            "Page size must be between 1 and $MAX_PAGE_SIZE"
        }
    }
}
