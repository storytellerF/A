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
    /** Number of successful executions for a task-type summary row. */
    val successCount: Long? = null,
    /** Number of failed executions for a task-type summary row. */
    val failureCount: Long? = null,
    /** Number of pending retries for a task-type summary row. */
    val retryRequestedCount: Long? = null,
) : PrimaryKeyIdentifiable
