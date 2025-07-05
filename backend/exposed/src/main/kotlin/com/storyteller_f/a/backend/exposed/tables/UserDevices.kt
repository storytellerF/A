package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.*

object UserDevices : Table() {
    val uid = customPrimaryKey("uid")
    val endpointUrl = varchar("endpoint_url", 100).uniqueIndex()
}

class UserDevice(val uid: PrimaryKey, val endpointUrl: String) {
    companion object {
        fun wrapRow(row: ResultRow): UserDevice {
            return with(UserDevices) {
                UserDevice(
                    row[uid],
                    row[endpointUrl]
                )
            }
        }
    }
}
