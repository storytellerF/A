package com.storyteller_f.a.cloud.cli

import com.storyteller_f.a.backend.exposed.tables.*
import com.storyteller_f.a.backend.exposed.tables.MemberJoins.objectId
import com.storyteller_f.a.backend.exposed.tables.MemberJoins.uid
import com.storyteller_f.a.backend.service.Backend
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.r2dbc.select

suspend fun Backend.getAllMembers(distinct: List<String>): Result<List<Triple<String, Long, String>>> =
    databaseSession.dbQuery {
        MemberJoins
            .join(Rooms, JoinType.INNER, objectId, Rooms.id)
            .join(Aids, JoinType.INNER, Rooms.id, Aids.objectId)
            .join(Users, JoinType.INNER, uid, Users.id)
            .select(Users.fields + Aids.value)
            .where {
                Aids.value inList distinct
            }.toList().map {
                Triple(it[Users.publicKey], it[Users.id], it[Aids.value])
            }
    }
