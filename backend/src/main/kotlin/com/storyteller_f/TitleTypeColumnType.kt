package com.storyteller_f

import com.storyteller_f.shared.type.TitleStatus
import com.storyteller_f.shared.type.TitleType
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table

class TitleTypeColumnType : ColumnType<TitleType>() {
    override fun sqlType(): String {
        return "varchar(10)"
    }

    override fun valueFromDB(value: Any): TitleType {
        if (value is TitleType) return value
        return TitleType.valueOf(value as String)
    }

    override fun notNullValueToDB(value: TitleType): Any {
        return value.name
    }
}

fun Table.titleType(name: String) = registerColumn(name, TitleTypeColumnType())

class TitleStatusColumnType : ColumnType<TitleStatus>() {
    override fun sqlType(): String {
        return "varchar(10)"
    }

    override fun valueFromDB(value: Any): TitleStatus {
        if (value is TitleStatus) return value
        return TitleStatus.valueOf(value as String)
    }

    override fun notNullValueToDB(value: TitleStatus): Any {
        return value.name
    }
}

fun Table.titleStatus(name: String) = registerColumn(name, TitleStatusColumnType())
