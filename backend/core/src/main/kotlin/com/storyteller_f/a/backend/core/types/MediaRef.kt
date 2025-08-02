package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

class MediaRef(val objectId: PrimaryKey, val objectType: ObjectType, val author: PrimaryKey, val mediaName: String) {
    companion object
}
