package com.storyteller_f.query

import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.map
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.UserDevice
import com.storyteller_f.tables.UserDevices
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

suspend fun ExposedDatabaseSession.addDevice(uid: PrimaryKey, endpointUrl: String) =
    dbQuery {
        check(UserDevices.insert {
            it[UserDevices.uid] = uid
            it[UserDevices.endpointUrl] = endpointUrl
        }.insertedCount > 0) {
            "Insert device failed"
        }
    }

suspend fun ExposedDatabaseSession.removeDevice(
    uid: PrimaryKey,
    endpointUrl: String
) =
    dbQuery {
        UserDevices.deleteWhere {
            (UserDevices.uid eq uid) and (UserDevices.endpointUrl eq endpointUrl)
        }
    }

suspend fun ExposedDatabaseSession.getUserDevices(uid: List<PrimaryKey>) = dbSearch {
    search {
        UserDevices.selectAll().where {
            UserDevices.uid inList uid
        }
    }
    map(UserDevice::wrapRow)
}
