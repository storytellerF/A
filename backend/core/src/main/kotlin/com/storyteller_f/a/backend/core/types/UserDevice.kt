package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.PrimaryKey

class UserDevice(val uid: PrimaryKey, val endpointUrl: String) {
    companion object
}
