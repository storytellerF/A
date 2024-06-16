package com.storyteller_f

import com.storyteller_f.shared.type.ObjectType
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table

class ObjectTypeColumnType : ColumnType<ObjectType>() {
    override fun sqlType(): String {
        return "varchar(10)"
    }

    override fun valueFromDB(value: Any): ObjectType {
        if (value is ObjectType) return value
        return ObjectType.valueOf(value as String)
    }

    override fun notNullValueToDB(value: ObjectType): Any {
        return value.name
    }

}

fun Table.objectType(name: String) = registerColumn(name, ObjectTypeColumnType())
