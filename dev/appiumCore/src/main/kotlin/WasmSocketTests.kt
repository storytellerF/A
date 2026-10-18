/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.client.core.signOutAll
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class BrowserSessionExpiry(val authenticated: AuthenticatedSession, val expire: () -> Unit)

fun testBrowserSocketServerExpiry(testName: String, target: AppAppiumHelper, platform: WasmAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(testName, target, setup = { ports ->
            AppiumTestSetup(
                BrowserSessionExpiry(target.createAuthenticatedSession(ports), checkNotNull(ports.expireUserSessions)),
            )
        }) { scope, data ->
            try {
                scenarioSignIn(scope.driver, data.authenticated.session.pemPrivateKey)
                scope.driver.dismissDialog()
                val probe = (scope.driver as WasmAppTestDriver).socketProbe()
                probe.awaitOpen()
                data.expire()
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByText("Sign in")
                probe.assertNoFurtherAttempts()
                probe.assertRevokedCookieRejected()
                scope.restart()
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByText("Sign in")
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

fun testBrowserSocketReconnect(testName: String, target: AppAppiumHelper, platform: WasmAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(testName, target, setup = { ports ->
            AppiumTestSetup(prepareAuthenticatedRoomScenario { target.createAuthenticatedSession(ports) })
        }) { scope, data ->
            try {
                scenarioSignIn(scope.driver, data.authenticated.session.pemPrivateKey)
                scope.driver.dismissDialog()
                val probe = (scope.driver as WasmAppTestDriver).socketProbe()
                probe.awaitOpen()
                scenarioPublishTopicInRoom(scope.driver, data.communityName, data.roomName)
                assertTrue(probe.count("sent") > 0)
                assertTrue(probe.count("received") > 0)
                val opened = probe.count("opened")
                val sent = probe.count("sent")
                probe.disconnect()
                probe.awaitOpen(opened)
                assertEquals(sent, probe.count("sent"), "Reconnection must not replay the published topic")
                val content = "socket-after-reconnect-${System.currentTimeMillis()}"
                scope.driver.inputText(content)
                scope.driver.clickByDescription("Send")
                scope.driver.assertVisibleByText(content)
                scope.restart()
                val restarted = (scope.driver as WasmAppTestDriver).socketProbe()
                restarted.awaitOpen()
                scenarioVerifyInjectedSessionLoaded(scope.driver, data.authenticated.session.address)
                scope.driver.clickByText("Sign out")
                scope.driver.clickByText("Yes")
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByText("Sign in")
                restarted.assertNoFurtherAttempts()
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

fun testBrowserSocketCookieLoss(testName: String, target: AppAppiumHelper, platform: WasmAppiumHelper) =
    testBrowserSocketInvalidCookie(testName, target, platform, expired = false)

fun testBrowserSocketCookieExpiry(testName: String, target: AppAppiumHelper, platform: WasmAppiumHelper) =
    testBrowserSocketInvalidCookie(testName, target, platform, expired = true)

private fun testBrowserSocketInvalidCookie(
    testName: String,
    target: AppAppiumHelper,
    platform: WasmAppiumHelper,
    expired: Boolean,
) = runAppiumBlockingTest {
    platform.runTest(testName, target, setup = { ports ->
        AppiumTestSetup(target.createAuthenticatedSession(ports))
    }) { scope, data ->
        try {
            scenarioSignIn(scope.driver, data.session.pemPrivateKey)
            scope.driver.dismissDialog()
            val probe = (scope.driver as WasmAppTestDriver).socketProbe()
            probe.awaitOpen()
            if (expired) probe.expireCookies() else probe.dropCookies()
            probe.disconnect()
            scope.driver.clickByDescription("avatar")
            scope.driver.assertVisibleByText("Sign in")
            probe.assertNoFurtherAttempts()
            scope.restart()
            scope.driver.clickByDescription("avatar")
            scope.driver.assertVisibleByText("Sign in")
        } finally {
            data.sessionManager.client.close()
        }
    }
}

fun testBrowserSocketOrigin(testName: String, target: AppAppiumHelper, platform: WasmAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(testName, target, setup = { ports ->
            AppiumTestSetup(target.createAuthenticatedSession(ports))
        }) { scope, data ->
            try {
                scenarioSignIn(scope.driver, data.session.pemPrivateKey)
                scope.driver.dismissDialog()
                val probe = (scope.driver as WasmAppTestDriver).socketProbe()
                probe.awaitOpen()
                probe.assertForeignOriginRejected()
            } finally {
                data.sessionManager.client.close()
            }
        }
    }

fun testBrowserSocketRevocation(testName: String, target: AppAppiumHelper, platform: WasmAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(testName, target, setup = { ports ->
            AppiumTestSetup(target.createAuthenticatedSession(ports))
        }) { scope, data ->
            try {
                scenarioSignIn(scope.driver, data.session.pemPrivateKey)
                scope.driver.dismissDialog()
                val probe = (scope.driver as WasmAppTestDriver).socketProbe()
                probe.awaitOpen()
                data.sessionManager.signOutAll().getOrThrow()
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByText("Sign in")
                probe.assertNoFurtherAttempts()
                probe.assertRevokedCookieRejected()
                scope.restart()
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByText("Sign in")
            } finally {
                data.sessionManager.client.close()
            }
        }
    }
