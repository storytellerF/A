package com.storyteller_f.shared.model

import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

interface PrimaryKeyIdentifiable {
    val id: PrimaryKey
}

interface ModelObject : PrimaryKeyIdentifiable {
    val objectType: ObjectType

    fun tuple() = id ob objectType
}
