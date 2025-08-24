package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import org.jetbrains.exposed.v1.core.ResultRow

object UploadRecords : BaseTable() {
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
    val total = long("total")
    val progress = long("progress")
    val name = varchar("name", 100)
}

fun UploadRecord.Companion.wrapRow(resultRow: ResultRow) {
    return with(UploadRecords) {
        UploadRecord(
            resultRow[id],
            resultRow[createdTime],
            resultRow[objectId],
            resultRow[objectType],
            resultRow[total],
            resultRow[progress],
            resultRow[name]
        )
    }
}
