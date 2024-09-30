package com.storyteller_f

import com.storyteller_f.shared.type.OKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

abstract class BaseTable : Table() {
    val id = ulong("id")
    val createdTime = datetime("created_time").index()

    override val primaryKey = PrimaryKey(id)
}

abstract class BaseObj(val id: OKey, val createdTime: LocalDateTime)
