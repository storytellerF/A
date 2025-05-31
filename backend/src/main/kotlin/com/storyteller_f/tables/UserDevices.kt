package com.storyteller_f.tables

import com.storyteller_f.Backend
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.map
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

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

suspend fun Backend.addDevice(uid: PrimaryKey, endpointUrl: String) =
    databaseSession.dbQuery {
        check(UserDevices.insert {
            it[UserDevices.uid] = uid
            it[UserDevices.endpointUrl] = endpointUrl
        }.insertedCount > 0) {
            "Insert device failed"
        }
    }

suspend fun Backend.removeDevice(uid: PrimaryKey, endpointUrl: String) =
    databaseSession.dbQuery {
        UserDevices.deleteWhere {
            (UserDevices.uid eq uid) and (UserDevices.endpointUrl eq endpointUrl)
        }
    }

suspend fun Backend.getUserDevices(uid: List<PrimaryKey>) = databaseSession.dbSearch {
    search {
        UserDevices.selectAll().where {
            UserDevices.uid inList uid
        }
    }
    map(UserDevice::wrapRow)
}
