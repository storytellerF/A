package com.storyteller_f.tables

import com.storyteller_f.customPrimaryKey
import com.storyteller_f.objectType
import org.jetbrains.exposed.sql.Table

object Aids : Table() {
    val value = varchar("value", 100).index()
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")

    init {
        index("aids-main", true, objectId, objectType)
    }
}
