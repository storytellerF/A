package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.customPrimaryKey
import com.storyteller_f.backend.service.objectType
import org.jetbrains.exposed.sql.Table

object Aids : Table() {
    val value = varchar("value", 100).uniqueIndex()
    val objectId = customPrimaryKey("object_id").uniqueIndex()
    val objectType = objectType("object_type")

    init {
        index("id-value", true, objectId, value)
    }
}
