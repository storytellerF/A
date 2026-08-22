/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class ObjectTuple(val objectId: PrimaryKey, val objectType: ObjectType)

infix fun PrimaryKey.ob(type: ObjectType): ObjectTuple = ObjectTuple(this, type)
