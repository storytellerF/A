package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.UserDevice
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import org.jetbrains.exposed.v1.core.*

object UserDevices : Table() {
    val uid = customPrimaryKey("uid")
    val endpointUrl = varchar("endpoint_url", 100).uniqueIndex()
}

fun UserDevice.Companion.wrapRow(row: ResultRow): UserDevice {
    return with(UserDevices) {
        UserDevice(row[uid], row[endpointUrl])
    }
}
