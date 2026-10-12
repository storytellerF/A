/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.shared.model.AlgoType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class ProtectedHistoryTest {
    @Test
    fun restoresSavedKeysAndClearsCurrentOnLogout() =
        runTest {
        var protected: String? = null
        val manager = ProtectedSessionHistoryManager("test", { protected }, { _, value -> protected = value })
        assertTrue(manager.getSavedSession().alias.isEmpty())
        manager.addSession(ACCOUNT.toRawUserPassInfo())
        val restored = ProtectedSessionHistoryManager("test", { protected }, { _, value -> protected = value })
        assertEquals(listOf("account"), restored.getSavedSession().alias)
        assertEquals(
            "private",
            (assertNotNull(restored.buildSession("account")) as RawUserPass).rawUSerPass.authKey.pemPrivateKey,
        )
        restored.exitSession("account")
        assertEquals(null, restored.getSavedSession().history?.current)
        assertEquals("account", restored.getSavedSession().history?.last)
        restored.removeSession("account")
        assertTrue(restored.getSavedSession().alias.isEmpty())
    }

    @Test
    fun unavailableStorageDoesNotUpdateSavedHistory() =
        runTest {
        val protected =
            Json.encodeToString(
                ProtectedSessionHistory(listOf(ACCOUNT), SessionHistory("account", "account")),
            )
        val manager = ProtectedSessionHistoryManager("test", { protected }, { _, _ -> error("Unavailable") })
        assertFailsWith<IllegalStateException> { manager.exitSession("account") }
        assertEquals("account", manager.getSavedSession().history?.current)
        val unverified = ProtectedSessionHistoryManager("test", { protected }, { _, _ -> Unit })
        assertFailsWith<IllegalStateException> { unverified.exitSession("account") }
        assertEquals("account", unverified.getSavedSession().history?.current)
    }

    companion object {
        private val ACCOUNT = ConvertedRawUserPassInfo(AlgoType.P256, "account", "private", "private-der", "public-der")
    }
}
