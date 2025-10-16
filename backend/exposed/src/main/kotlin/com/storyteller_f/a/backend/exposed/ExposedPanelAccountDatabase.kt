package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.PanelAccountDatabase
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.core.types.RawPanelAccount
import com.storyteller_f.a.backend.exposed.tables.PanelAccounts
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
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
                it[passType] = panelAccount.passType
                it[algoType] = panelAccount.algoType
                it[publicKey] = panelAccount.publicKey
                it[address] = panelAccount.address
            }
        }
    }

    private suspend fun getUserAuthDataBy(
        predicate: () -> Op<Boolean>,
    ): Result<Pair<String, Long>?> {
        return databaseSession.dbSearch {
            search {
                PanelAccounts.select(listOf(PanelAccounts.publicKey, PanelAccounts.id)).where(predicate)
            }
            first {
                it[PanelAccounts.publicKey] to it[PanelAccounts.id]
            }
        }
    }

    override suspend fun getUserAuthDataById(id: PrimaryKey): Result<Pair<String, Long>?> {
        return getUserAuthDataBy {
            PanelAccounts.id eq id
        }
    }

    override suspend fun getUserAuthDataByAddress(address: String): Result<Pair<String, Long>?> {
        return getUserAuthDataBy {
            PanelAccounts.address eq address
        }
    }

    override suspend fun getRawUserAndPublicKeyByAddress(
        ad: String,
    ): Result<Pair<RawPanelAccount, String>?> {
        return databaseSession.dbSearch {
            search {
                PanelAccounts
                    .selectAll()
                    .where {
                        PanelAccounts.address eq ad
                    }
            }
            first {
                val value = PanelAccount.wrapRow(it)
                Pair(RawPanelAccount(value.id, value.name), value.publicKey)
            }
        }
    }
    override suspend fun isUserNotExistsByPublicKey(pk: String): Result<Boolean> {
        return databaseSession.dbSearch {
            search {
                PanelAccounts.selectAll().where {
                    PanelAccounts.publicKey eq pk
                }
            }
            isEmpty()
        }
    }
}
