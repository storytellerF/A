package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TaskRecordType
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow

object TaskRecords : BaseTable() {
    val type = enumerationByName<TaskRecordType>("type", 10)
    val processedId = customPrimaryKey("processed_id")

    init {
        index("task-records-main", false, type)
    }
}

class TaskRecord(id: PrimaryKey, createdTime: LocalDateTime, val type: TaskRecordType, val processedId: PrimaryKey) :
    BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): TaskRecord {
            return with(TaskRecords) {
                TaskRecord(
                    resultRow[id],
                    resultRow[createdTime],
                    resultRow[type],
                    resultRow[processedId]
                )
            }
        }
    }
}
