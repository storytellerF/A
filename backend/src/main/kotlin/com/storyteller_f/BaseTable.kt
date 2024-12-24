package com.storyteller_f

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

abstract class BaseTable : Table() {
    val id = customPrimaryKey("id")
    val createdTime = datetime("created_time")

    override val primaryKey = PrimaryKey(id)
}

abstract class BaseObj(val id: PrimaryKey, val createdTime: LocalDateTime)

fun Table.customPrimaryKey(name: String) = long(name)
