package com.storyteller_f.shared.model

import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

interface Identifiable {
    val id: PrimaryKey

    val objectType: ObjectType

    fun tuple(): ObjectTuple {
        return id ob objectType
    }
}
