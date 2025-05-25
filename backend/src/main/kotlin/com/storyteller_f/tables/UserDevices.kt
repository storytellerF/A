package com.storyteller_f.tables

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
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

suspend fun DatabaseFactory.addDevice(uid: PrimaryKey, endpointUrl: String, backend: Backend) =
    dbQuery(backend) {
        check(UserDevices.insert {
            it[UserDevices.uid] = uid
            it[UserDevices.endpointUrl] = endpointUrl
        }.insertedCount > 0) {
            "Insert device failed"
        }
    }

suspend fun DatabaseFactory.removeDevice(uid: PrimaryKey, endpointUrl: String, backend: Backend) =
    dbQuery(backend) {
        UserDevices.deleteWhere {
            (UserDevices.uid eq uid) and (UserDevices.endpointUrl eq endpointUrl)
        }
    }

suspend fun DatabaseFactory.getUserDevices(backend: Backend, uid: List<PrimaryKey>) = dbSearch(
    backend,
) {
    search {
        UserDevices.selectAll().where {
            UserDevices.uid inList uid
        }
    }
    map(UserDevice::wrapRow)
}
