package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

data class ObjectTuple(val objectId: PrimaryKey, val objectType: ObjectType)

infix fun PrimaryKey.ob(type: ObjectType): ObjectTuple {
    return ObjectTuple(this, type)
}
