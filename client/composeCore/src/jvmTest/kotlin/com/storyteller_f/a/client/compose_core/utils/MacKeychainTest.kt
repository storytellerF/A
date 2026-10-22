/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import com.sun.jna.Platform
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class MacKeychainTest {
    @Test
    fun roundTripsLargeUnicodeValues() {
        assumeTrue(Platform.isMac())
        val service = "desktop-keychain-test-${UUID.randomUUID()}"
        val account = "round-trip"
        val value = "账户-data-".repeat(4096)
        try {
            assertNull(MacKeychain.read(service, account))
            MacKeychain.write(service, account, value)
            assertEquals(value, MacKeychain.read(service, account))
            MacKeychain.write(service, account, "replacement")
            assertEquals("replacement", MacKeychain.read(service, account))
            MacKeychain.write(service, account, "")
            assertEquals("", MacKeychain.read(service, account))
        } finally {
            MacKeychain.delete(service, account)
        }
        assertNull(MacKeychain.read(service, account))
    }
}
