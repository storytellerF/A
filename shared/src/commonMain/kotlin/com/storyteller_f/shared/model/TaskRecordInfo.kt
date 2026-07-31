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
}

@Serializable
/** Outcome of an individual task-object execution. */
enum class TaskRecordStatus {
    /** The execution completed successfully. */
    SUCCESS,
    /** The execution did not complete successfully. */
    FAILURE,
}

@Serializable
/** Category used to group task execution failures. */
enum class TaskFailureType {
    /** The moderation model returned an invalid decision. */
    MODEL_RESPONSE,
    /** The moderation model could not execute. */
    MODEL_EXECUTION,
    /** A required persisted object could not be accessed. */
    DATA_ACCESS,
    /** A failure that has not been classified. */
    UNKNOWN,
}

@Serializable
/** Aggregate counts for one task type. */
data class TaskRecordSummary(
    /** The task type represented by these counts. */
    val type: TaskRecordType,
    /** Number of successful executions. */
    val successCount: Long,
    /** Number of failed executions. */
    val failureCount: Long,
    /** Number of failures manually selected for retry. */
    val retryRequestedCount: Long,
)

@Serializable
data class TaskRecordInfo(
    override val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val objectId: PrimaryKey,
    val type: TaskRecordType,
    /** Whether this execution succeeded or failed. */
    val status: TaskRecordStatus = TaskRecordStatus.SUCCESS,
    /** Classification of the failure, when the execution failed. */
    val failureType: TaskFailureType? = null,
    /** Safe diagnostic reason for the failure, when available. */
    val failureReason: String? = null,
    /** Whether an administrator requested this failed execution be retried. */
    val retryRequested: Boolean = false,
) : PrimaryKeyIdentifiable
