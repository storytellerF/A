package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import org.jetbrains.exposed.sql.Table

object Aids : Table() {
    val value = varchar("value", 100).uniqueIndex()
    val objectId = customPrimaryKey("object_id").uniqueIndex()
    val objectType = objectType("object_type")

    init {
        index("id-value", true, objectId, value)
    }
}
