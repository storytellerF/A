/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.client.core.ServerErrorException
import com.storyteller_f.a.client.core.addChildAccount
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.restoreSession
import com.storyteller_f.a.client.core.signOutAll
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertIs

fun testSignUpByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { AppiumTestSetup(Unit) },
        ) { scope, _ ->
            scenarioSignUp(scope.driver)
        }
    }

fun testSignInAsSystemUserByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { AppiumTestSetup(Unit) },
        ) { scope, _ ->
            scenarioSignInAsSystemUser(scope.driver, target.readSystemPrivateKey())
        }
    }

fun testInjectedSessionByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports ->
                val session = target.createPreRegisteredSession(ports)
                AppiumTestSetup(
                    data = session.address,
                    injectedSession = session,
                )
            },
        ) { scope, address ->
            scenarioVerifyInjectedSessionLoaded(scope.driver, address)
        }
    }

fun testPublishTopicInUserSpaceByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports ->
                val session = target.createPreRegisteredSession(ports)
                AppiumTestSetup(data = session.address, injectedSession = session)
            },
        ) { scope, address ->
            scenarioPublishTopicInUserSpace(scope.driver, address)
        }
    }

fun testFavoriteTopicByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports ->
                val data = prepareFavoriteTopicScenario { target.createAuthenticatedSession(ports) }
                AppiumTestSetup(data = data, injectedSession = data.authenticated.session)
            },
        ) { scope, data ->
            try {
                scenarioFavoritePreparedTopic(scope.driver, data)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

fun testOpenAsciidocPreviewByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            captureBrowserOpen = platform.capturesExternalAsciidocPreview,
            setup = { ports ->
                val data = prepareAsciidocPreviewScenario { target.createAuthenticatedSession(ports) }
                AppiumTestSetup(data = data, injectedSession = data.authenticated.session)
            },
        ) { scope, data ->
            try {
                scenarioOpenAsciidocPreview(
                    scope.driver,
                    data.authenticated.session.address,
                    data.topicMarker,
                )
                scope.assertAsciidocPreviewOpened(data.asciidocSource)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

fun testSubscribeTopicByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports ->
                val data = prepareSubscriptionTopicScenario { target.createAuthenticatedSession(ports) }
                AppiumTestSetup(data = data, injectedSession = data.authenticated.session)
            },
        ) { scope, data ->
            try {
                scenarioSubscribePreparedTopic(scope.driver, data)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

fun testCommunityProfileActionsByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    testPreparedCommunityRoomScenarioByHelper(testName, target, platform) { driver, data ->
        scenarioCommunityProfileActions(driver, data.communityName, data.ownerSession.address)
    }

fun testPublishTopicInCommunityByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    testPreparedCommunityRoomScenarioByHelper(testName, target, platform) { driver, data ->
        scenarioPublishTopicInCommunity(driver, data.communityName)
    }

fun testPublishTopicInCommunityRoomByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = testPreparedCommunityRoomScenarioByHelper(testName, target, platform) { driver, data ->
    scenarioPublishTopicInRoom(driver, data.communityName, data.roomName)
}

fun testPanelInjectedSessionByHelper(testName: String, target: PanelAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports ->
                val session = target.createPreRegisteredSession(ports)
                AppiumTestSetup(data = Unit, injectedSession = session)
            },
        ) { scope, _ ->
            scenarioOpenAllUsersFromOverview(scope.driver, platform is DesktopAppiumHelper)
        }
    }

private fun testPreparedCommunityRoomScenarioByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
    block: suspend (AppTestDriver, PreparedCommunityRoomScenario) -> Unit,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            val data = prepareCommunityRoomScenario { target.createAuthenticatedSession(ports) }
            AppiumTestSetup(data = data, injectedSession = data.viewerSession)
        },
    ) { scope, data ->
        block(scope.driver, data)
    }
}

fun testSigninLifecycleByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports -> AppiumTestSetup(target.createAuthenticatedSession(ports)) },
        ) { scope, data ->
            try {
                scenarioSignIn(scope.driver, data.session.pemPrivateKey)
                scope.restart()
                scenarioVerifyInjectedSessionLoaded(scope.driver, data.session.address)
                if (platform is WasmAppiumHelper) scope.driver.assertNotVisibleByText("Switch Account")
                scope.driver.dismissDialog()
                scenarioOpenSessionRoom(scope.driver, data)
                data.sessionManager.signOutAll().getOrThrow()
                assertEquals(
                    HttpStatusCode.Unauthorized,
                    assertIs<ServerErrorException>(data.sessionManager.restoreSession().exceptionOrNull()).status,
                )
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByText("Sign in")
                scope.restart()
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByText("Sign in")
            } finally {
                data.sessionManager.client.close()
            }
        }
    }

fun testPanelSigninLifecycleByHelper(testName: String, target: PanelAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports -> AppiumTestSetup(target.createAuthenticatedSession(ports)) },
        ) { scope, data ->
            try {
                scope.driver.clickByText("Sign in")
                scope.driver.clickByText("Input")
                scope.driver.clickByDescription("Edit Private Key")
                scope.driver.inputText(data.session.pemPrivateKey)
                scope.driver.clickByText("Confirm")
                scope.driver.clickByText("Start")
                scope.driver.assertVisibleByDescription("Menu")
                scope.restart()
                scope.driver.assertVisibleByDescription("Menu")
                data.sessionManager.signOutAll().getOrThrow()
                assertEquals(
                    HttpStatusCode.Unauthorized,
                    assertIs<ServerErrorException>(data.sessionManager.restoreSession().exceptionOrNull()).status,
                )
                scope.restart()
                scope.driver.assertVisibleByText("Sign in")
                scope.restart()
                scope.driver.assertVisibleByText("Sign in")
            } finally {
                data.sessionManager.client.close()
            }
        }
    }

fun testChildSigninByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports -> AppiumTestSetup(target.createAuthenticatedSession(ports)) },
        ) { scope, data ->
            try {
                val child = data.sessionManager.addChildAccount().getOrThrow()
                scenarioSignIn(scope.driver, data.session.pemPrivateKey)
                scope.driver.clickByText("Switch Account")
                scope.driver.clickByTextContaining(child.userInfo.address)
                scope.driver.clickByDescription("avatar")
                scope.driver.assertVisibleByTextContaining(child.userInfo.address)
                scope.restart()
                scenarioVerifyInjectedSessionLoaded(scope.driver, data.session.address)
            } finally {
                data.sessionManager.client.close()
            }
        }
    }

fun testSignoutPersistenceByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports -> AppiumTestSetup(target.createPreRegisteredSession(ports)) },
        ) { scope, data ->
            scenarioSignIn(scope.driver, data.pemPrivateKey)
            scope.driver.clickByText("Sign out")
            scope.driver.clickByText("Yes")
            scope.restart()
            scope.driver.clickByDescription("avatar")
            scope.driver.assertVisibleByText("Sign in")
        }
    }

private suspend fun scenarioOpenSessionRoom(driver: AppTestDriver, data: AuthenticatedSession) {
    val suffix = System.currentTimeMillis().toString()
    val communityName = "sc-$suffix"
    val roomName = "sr-$suffix"
    val community = data.sessionManager.createCommunity(NewCommunity(communityName, "sc$suffix")).getOrThrow()
    data.sessionManager.createRoom(NewRoom(roomName, "sr$suffix", communityId = community.id)).getOrThrow()
    scenarioPublishTopicInRoom(driver, communityName, roomName)
}
