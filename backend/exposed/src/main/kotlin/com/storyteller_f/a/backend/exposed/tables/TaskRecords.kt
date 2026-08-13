package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.taskRecordType
import com.storyteller_f.shared.model.LlmConfig
import com.storyteller_f.shared.model.TaskConfig
import com.storyteller_f.shared.model.TaskRecordType
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert

object TaskRecords : BaseTable() {
    val type = taskRecordType("type")

    /** Identifier of the object processed by the task execution. */
    val objectId: Column<Long> = customPrimaryKey("object_id")

    /** Whether the task execution completed successfully. */
    val success: Column<Boolean> = bool("is_success").default(true)

    /** Optional machine-readable failure category. */
    val failureType: Column<String?> = varchar("failure_type", FAILURE_TYPE_LENGTH).nullable()

    /** Optional safe diagnostic failure reason. */
    val failureReason: Column<String?> = text("failure_reason").nullable()

    /** Whether an administrator requested another execution attempt. */
    val retryRequested: Column<Boolean> = bool("is_retry_requested").default(false)

    init {
        index("task-records-main", false, type)
        index("task-records-filter", false, type, success, failureType, retryRequested)
    }
}

fun TaskRecord.Companion.wrapRow(resultRow: ResultRow): TaskRecord {
    val taskRecord =
        with(TaskRecords) {
            val rowFailureType =
                if (resultRow[success]) {
                    null
                } else {
                    resultRow[failureType] ?: TaskRecordType.UNKNOWN_FAILURE
                }
            TaskRecord(
                id = resultRow[id],
                createdTime = resultRow[createdTime],
                objectId = resultRow[objectId],
                type = resultRow[type],
                failureType = rowFailureType,
                failureReason = resultRow[failureReason],
                isRetryRequested = resultRow[retryRequested],
            )
        }
    return taskRecord
}

suspend fun addTaskRecord(taskRecord: TaskRecord) {
    check(
        TaskRecords.insert { statement ->
            statement[TaskRecords.id] = taskRecord.id
            statement[TaskRecords.createdTime] = taskRecord.createdTime
            statement[TaskRecords.type] = taskRecord.type
            statement[TaskRecords.objectId] = taskRecord.objectId
            statement[TaskRecords.success] = taskRecord.isSuccess
            statement[TaskRecords.failureType] = taskRecord.failureType
            statement[TaskRecords.failureReason] = taskRecord.failureReason
            statement[TaskRecords.retryRequested] = taskRecord.isRetryRequested
        }.insertedCount > 0,
    ) {
        "Insert task record failed"
    }
}

private const val FAILURE_TYPE_LENGTH = 20

/** Persisted runtime configuration for worker tasks. */
object TaskConfigs : Table("task_configs") {
    /** Configured worker task type. */
    val type: Column<TaskRecordType> = taskRecordType("type")

    /** Whether the task may execute. */
    val enabled: Column<Boolean> = bool("enabled")

    /** Maximum number of objects fetched per iteration. */
    val fetchSize: Column<Int> = integer("fetch_size")

    /** Delay after an enabled iteration, in milliseconds. */
    val waitDurationMillis: Column<Long> = long("wait_duration_millis")

    override val primaryKey: PrimaryKey = PrimaryKey(type)
}

/** Maps a task configuration database row to its shared model. */
internal fun TaskConfig.Companion.wrapRow(resultRow: ResultRow): TaskConfig {
    val config =
        with(TaskConfigs) {
            TaskConfig(
                type = resultRow[type],
                isEnabled = resultRow[enabled],
                fetchSize = resultRow[fetchSize],
                waitDurationMillis = resultRow[waitDurationMillis],
            )
        }
    return config
}

/** Generic persisted backend configuration values. */
object BackendConfigs : Table("backend_configs") {
    /** Unique configuration key. */
    val key: Column<String> = varchar("key", CONFIG_KEY_MAX_LENGTH)

    /** Discriminator describing which value column is populated. */
    val valueType: Column<String> = varchar("value_type", CONFIG_TYPE_MAX_LENGTH)

    /** Optional integer configuration value. */
    val valueInt: Column<Int?> = integer("value_int").nullable()

    /** Optional Boolean configuration value. */
    val valueBool: Column<Boolean?> = bool("value_bool").nullable()

    /** Optional string configuration value. */
    val valueString: Column<String?> = text("value_string").nullable()

    /** Optional serialized JSON configuration value. */
    val valueJson: Column<String?> = text("value_json").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(key)
}

/** Key used to persist the active LLM configuration. */
const val LLM_CONFIG_KEY: String = "llm_config"

private const val CONFIG_KEY_MAX_LENGTH = 100
private const val CONFIG_TYPE_MAX_LENGTH = 20
private const val JSON_CONFIG_TYPE = "json"
private val configJson = Json { ignoreUnknownKeys = true }

/** Maps a generic configuration row to its LLM model. */
internal fun LlmConfig.Companion.fromConfigRow(resultRow: ResultRow): LlmConfig? {
    val valueJson =
        resultRow[BackendConfigs.valueJson]
            ?.takeIf { resultRow[BackendConfigs.valueType] == JSON_CONFIG_TYPE }
    return valueJson?.let { configJson.decodeFromString<LlmConfig>(it) }
}

/** Maps an LLM model to values for the generic configuration table. */
internal fun LlmConfig.toConfigRow(): Map<Column<*>, Any?> {
    val values: Map<Column<*>, Any?> =
        with(BackendConfigs) {
            mapOf(
                key to LLM_CONFIG_KEY,
                valueType to JSON_CONFIG_TYPE,
                valueJson to configJson.encodeToString(LlmConfig.serializer(), this@toConfigRow),
            )
        }
    return values
}
