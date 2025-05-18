package com.storyteller_f.tables

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

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

suspend fun DatabaseFactory.getUserDevices(backend: Backend, uid: List<PrimaryKey>) = mapQuery(
    backend,
    UserDevice::wrapRow
) {
    UserDevices.selectAll().where {
        UserDevices.uid inList uid
    }
}
