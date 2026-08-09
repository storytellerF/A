/*
 * This is a private project. All rights reserved.
 */
package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.shared.model.LlmConfig
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

/**
 * Generic configuration table for storing various types of configurations.
 * This table supports storing int, bool, string, and JSON values.
 */
object BackendConfigs : Table("backend_configs") {
    /** Configuration key (primary key) */
    val key: Column<String> = varchar("key", 100)

    /** Value type: "int", "bool", "string", "json" */
    val valueType: Column<String> = varchar("value_type", 20)

    /** Integer value */
    val valueInt: Column<Int?> = integer("value_int").nullable()

    /** Boolean value */
    val valueBool: Column<Boolean?> = bool("value_bool").nullable()

    /** String value */
    val valueString: Column<String?> = text("value_string").nullable()

    /** JSON value (serialized JSON string) */
    val valueJson: Column<String?> = text("value_json").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(key)
}

/** LLM configuration key */
const val LLM_CONFIG_KEY = "llm_config"

/** JSON serializer for LLM configuration */
private val json = Json { ignoreUnknownKeys = true }

/** Maps an LLM configuration from the generic config table. */
internal fun LlmConfig.Companion.fromConfigRow(resultRow: ResultRow): LlmConfig? {
    val config =
        with(BackendConfigs) {
            val valueType = resultRow[valueType]
            if (valueType != "json") return null

            val valueJson = resultRow[valueJson] ?: return null
            json.decodeFromString<LlmConfig>(valueJson)
        }
    return config
}

/** Maps an LLM configuration to a generic config row for storage. */
internal fun LlmConfig.toConfigRow(): Map<Column<*>, Any?> =
    with(BackendConfigs) {
        mapOf(
            key to LLM_CONFIG_KEY,
            valueType to "json",
            valueJson to json.encodeToString(LlmConfig.serializer(), this@toConfigRow),
        )
    }
