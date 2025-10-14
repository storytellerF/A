package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.PanelAccountDatabase
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.exposed.tables.PanelAccounts
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

class ExposedPanelAccountDatabase(val databaseSession: ExposedDatabaseSession) : PanelAccountDatabase {
    override suspend fun getPanelAccount(id: PrimaryKey): Result<PanelAccount?> {
        return databaseSession.dbSearch {
            search {
                PanelAccounts.selectAll().where {
                    PanelAccounts.id eq id
                }
            }
            first(PanelAccount::wrapRow)
        }
    }

    override suspend fun addPanelAccount(panelAccount: PanelAccount): Result<Unit> {
        return databaseSession.dbQuery {
            PanelAccounts.insert {
                it[id] = panelAccount.id
                it[name] = panelAccount.name
                it[createdTime] = now()
            }
        }
    }
}
