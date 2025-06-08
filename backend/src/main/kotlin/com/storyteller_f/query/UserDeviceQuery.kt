package com.storyteller_f.query

import com.storyteller_f.Backend
import com.storyteller_f.map
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.UserDevice
import com.storyteller_f.tables.UserDevices
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

suspend fun Backend.addDevice(uid: PrimaryKey, endpointUrl: String) =
    exposedDatabaseSession.dbQuery {
        check(UserDevices.insert {
            it[UserDevices.uid] = uid
            it[UserDevices.endpointUrl] = endpointUrl
        }.insertedCount > 0) {
            "Insert device failed"
        }
    }

suspend fun Backend.removeDevice(uid: PrimaryKey, endpointUrl: String) =
    exposedDatabaseSession.dbQuery {
        UserDevices.deleteWhere {
            (UserDevices.uid eq uid) and (UserDevices.endpointUrl eq endpointUrl)
        }
    }

suspend fun Backend.getUserDevices(uid: List<PrimaryKey>) = exposedDatabaseSession.dbSearch {
    search {
        UserDevices.selectAll().where {
            UserDevices.uid inList uid
        }
    }
    map(UserDevice::wrapRow)
}
