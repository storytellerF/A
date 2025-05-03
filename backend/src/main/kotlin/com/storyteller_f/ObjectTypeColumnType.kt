package com.storyteller_f

import com.storyteller_f.shared.type.ObjectType
import org.jetbrains.exposed.sql.Table

fun Table.objectType(name: String) = enumerationByName<ObjectType>(name, 10)
