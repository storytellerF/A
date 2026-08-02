package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

enum class TaskRecordType {
    TOPIC_ACG,
    INTRO,
    SUBSCRIPTION,
    TITLE,

    /** Records the last topic examined by automated content moderation. */
    TOPIC_MODERATION,

    ;

    /** Stable failure classifications serialized by the API and database. */
    companion object {
        /** Failure caused by a malformed model response. */
        const val MODEL_RESPONSE_FAILURE: String = "MODEL_RESPONSE"

        /** Failure caused while executing a model. */
        const val MODEL_EXECUTION_FAILURE: String = "MODEL_EXECUTION"

        /** Failure caused while accessing persisted data. */
        const val DATA_ACCESS_FAILURE: String = "DATA_ACCESS"

        /** Failure without a more specific classification. */
        const val UNKNOWN_FAILURE: String = "UNKNOWN"
    }
}

@Serializable
data class TaskRecordInfo(
    override val id: PrimaryKey,
    val createdTime: LocalDateTime,
    /** Identifier of the business object processed by this execution. */
    val objectId: PrimaryKey,
    val type: TaskRecordType,
    /** Whether this execution completed successfully. */
    val isSuccess: Boolean = true,
    /** Classification of the failure, when the execution failed. */
    val failureType: String? = null,
    /** Safe diagnostic reason for the failure, when available. */
    val failureReason: String? = null,
    /** Whether an administrator requested this failed execution be retried. */
    val isRetryRequested: Boolean = false,
) : PrimaryKeyIdentifiable

/** Aggregate execution counts for one worker task type. */
@Serializable
data class TaskRecordSummary(
    /** Worker task type represented by these counts. */
    val type: TaskRecordType,
    /** Number of successful executions. */
    val successCount: Long,
    /** Number of failed executions. */
    val failureCount: Long,
    /** Number of failed executions awaiting an administrator-requested retry. */
    val retryRequestedCount: Long,
)

/** Runtime configuration for one worker task type. */
@Serializable
data class TaskConfig(
    /** Worker task controlled by this configuration. */
    val type: TaskRecordType,
    /** Whether the worker may execute this task. */
    val isEnabled: Boolean,
    /** Maximum number of business objects fetched in one task iteration. */
    val fetchSize: Int,
    /** Delay after an enabled task iteration, in milliseconds. */
    val waitDurationMillis: Long,
) {
    init {
        require(fetchSize > 0) { "fetchSize must be greater than zero" }
        require(waitDurationMillis > 0) { "waitDurationMillis must be greater than zero" }
    }

    /** Marker used by database row mappers. */
    companion object
}
