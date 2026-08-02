/*
 * This is a private project. All rights reserved.
 */

package com.storyteller.a.client.core

import com.storyteller_f.a.client.core.buildWebSocketUrl
import kotlin.test.Test
import kotlin.test.assertEquals

internal class HttpClientTest {
    @Test
    internal fun buildWebSocketUrlUsesWsPath() {
        assertEquals("wss://api.example.test/ws", buildWebSocketUrl("wss://api.example.test"))
    }
}
