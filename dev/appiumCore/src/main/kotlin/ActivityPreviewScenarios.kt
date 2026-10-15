/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.addReadLog
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.getRoomMembersPublicKeys
import com.storyteller_f.a.client.core.getUserRooms
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.pinTopic
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

private data class ActivityScenario(
    val viewer: AuthenticatedSession,
    val roomId: Long,
    val roomName: String,
    val latestMessage: String,
    val pinnedTopic: String?,
)

fun testActivityPreviewByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
    privateRoom: Boolean = false,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            val data =
                if (privateRoom) {
                    preparePrivateActivityScenario { target.createAuthenticatedSession(ports) }
                } else {
                    prepareActivityScenario { target.createAuthenticatedSession(ports) }
                }
            AppiumTestSetup(data)
        },
    ) { scope, data ->
        try {
            scenarioSignIn(scope.driver, data.viewer.session.pemPrivateKey)
            scope.driver.dismissDialog()
            scenarioReadAndSendActivity(scope.driver, data)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (failure: IllegalStateException) {
            scope.driver.saveSnapshot("activity-failed")
            throw failure
        } finally {
            data.viewer.sessionManager.client.close()
        }
    }
}

private suspend fun prepareActivityScenario(createSession: suspend () -> AuthenticatedSession): ActivityScenario {
    val owner = createSession()
    try {
        val viewer = createSession()
        var retained = false
        try {
            val suffix = (System.currentTimeMillis() % 1_000_000).toString().padStart(6, '0')
            val communityId =
                owner.sessionManager.createCommunity(NewCommunity("activity-$suffix", "ac$suffix"))
                    .getOrThrow().id
            val roomName = "activity-room-$suffix"
            val roomId =
                owner.sessionManager.createRoom(
                    NewRoom(roomName, "ar$suffix", communityId = communityId),
                )
                    .getOrThrow().id
            viewer.sessionManager.joinCommunity(communityId).getOrThrow()
            viewer.sessionManager.joinRoom(roomId).getOrThrow()
            val pinnedTopic = "activity-pinned-$suffix"
            val topicId =
                owner.sessionManager.createTopic(
                    ObjectType.COMMUNITY,
                    communityId,
                    pinnedTopic,
                ).getOrThrow().id
            owner.sessionManager.pinTopic(topicId).getOrThrow()
            val messages = (1..3).map { "activity-message-$suffix-$it" }
            sendActivityMessages(owner, roomId, messages)
            val scenario = ActivityScenario(viewer, roomId, roomName, messages.last(), pinnedTopic)
            retained = true
            return scenario
        } finally {
            if (!retained) viewer.sessionManager.client.close()
        }
    } finally {
        owner.sessionManager.client.close()
    }
}

private suspend fun scenarioReadAndSendActivity(driver: AppTestDriver, data: ActivityScenario) {
    // Native Firefox windows enforce a 500px minimum; this still exercises bottom navigation.
    val compactWidth = if (driver is WasmAppTestDriver) 500 else 480
    driver.clickByText("Rooms")
    driver.assertVisibleByTextContaining(data.latestMessage)
    driver.assertVisibleByText("3")
    driver.assertActivityUnreadIndicator(true)
    scenarioResizeActivityRooms(driver, data, compactWidth)
    val showContext =
        when (driver) {
            is DesktopAppTestDriver, is WasmAppTestDriver -> true
            is AndroidAppTestDriver -> driver.isWideViewport()
            else -> false
        }
    if (data.pinnedTopic != null && showContext) {
        scenarioOpenActivityContext(driver, data)
    }
    driver.clickByTextContaining(data.roomName)
    driver.assertVisibleByDescription("Send")
    driver.assertVisibleByText(data.latestMessage)
    waitUntilRoomRead(data)
    driver.dismissKeyboard()
    driver.navigateBack()
    driver.assertVisibleByTextContaining(data.latestMessage)
    driver.assertNotVisibleByText("3")
    driver.assertActivityUnreadIndicator(false)
    driver.clickByTextContaining(data.roomName)
    driver.assertVisibleByDescription("Send")
    val sentMessage = "activity-sent-${System.currentTimeMillis()}"
    driver.inputText(sentMessage)
    if (driver is DesktopAppTestDriver || driver is WasmAppTestDriver) {
        listOf(compactWidth to 800, 900 to 800, 1600 to 500, 1600 to 1000).forEach { (width, height) ->
            driver.resizeActivityViewport(width, height)
            if (driver is WasmAppTestDriver) {
                driver.assertInputText(sentMessage)
            } else {
                driver.assertVisibleByText(sentMessage)
            }
            driver.assertVisibleByDescription("Send")
            driver.saveSnapshot("activity-draft-$width-$height")
        }
    }
    driver.assertVisibleByDescription("Send")
    driver.clickByDescription("Send")
    driver.assertVisibleByText(sentMessage)
    waitUntilRoomRead(data)
    driver.dismissKeyboard()
    driver.navigateBack()
    driver.assertVisibleByTextContaining(sentMessage)
    driver.assertActivityUnreadIndicator(false)
    scenarioRevisitActivityRoom(driver, data.roomName, sentMessage)
    driver.saveSnapshot("activity-after-read-send")
}

private suspend fun waitUntilRoomRead(data: ActivityScenario) {
    val deadline = System.currentTimeMillis() + java.time.Duration.ofSeconds(30).toMillis()
    while (System.currentTimeMillis() < deadline) {
        val count =
            data.viewer.sessionManager.getUserRooms(PaginationQuery()).getOrThrow()
                .activities.single { it.room.id == data.roomId }.unreadMessageCount
        if (count == 0L) return
        delay(500.milliseconds)
    }
    error("Reading the room did not clear its unread messages")
}

private suspend fun sendActivityMessages(
    owner: AuthenticatedSession,
    roomId: Long,
    messages: List<String>,
    privateRoom: Boolean = false,
) = coroutineScope {
    val keys =
        if (privateRoom) {
            owner.sessionManager.getRoomMembersPublicKeys(roomId, PaginationQuery()).getOrThrow()
                .data.toList()
        } else {
            emptyList()
        }
    val socket = owner.sessionManager.webSocketClient
    val connection = launch { socket.connectWebSocket() }
    try {
        withTimeout(30_000) {
            socket.connectionHandler.data.filterNotNull().first()
            socket.useWebSocket {
                messages.forEach { sendMessage(ObjectTuple(roomId, ObjectType.ROOM), privateRoom, it, keys) }
            }.getOrThrow()
            while (owner.sessionManager.getUserRooms(PaginationQuery()).getOrThrow()
                    .activities.single { it.room.id == roomId }.unreadMessageCount != messages.size.toLong()
            ) {
                delay(500.milliseconds)
            }
        }
    } finally {
        connection.cancelAndJoin()
    }
}

private suspend fun scenarioOpenActivityContext(driver: AppTestDriver, data: ActivityScenario) {
    val pinnedTopic = requireNotNull(data.pinnedTopic)
    driver.clickByText("World")
    driver.assertVisibleByText("Unread rooms")
    driver.assertVisibleByText("Pinned topics")
    driver.clickByTextContaining(data.roomName)
    driver.assertVisibleByDescription("Send")
    driver.assertVisibleByText(data.latestMessage)
    waitUntilRoomRead(data)
    driver.navigateBack()
    driver.assertVisibleByText("Unread rooms")
    driver.assertVisibleByTextContaining(pinnedTopic)
    if (driver is WasmAppTestDriver) {
        driver.clickByDescription("pinned topic preview")
    } else {
        driver.clickByTextContaining(pinnedTopic)
    }
    driver.assertVisibleByDescription("topic")
    driver.navigateBack()
    driver.assertVisibleByText("Unread rooms")
    driver.clickByText("Rooms")
}

private suspend fun preparePrivateActivityScenario(
    createSession: suspend () -> AuthenticatedSession,
): ActivityScenario {
    val viewer = createSession()
    var retained = false
    try {
        val suffix = (System.currentTimeMillis() % 1_000_000).toString().padStart(6, '0')
        val roomName = "private-$suffix"
        val roomId = viewer.sessionManager.createRoom(NewRoom(roomName, "pa$suffix")).getOrThrow().id
        val messages = (1..3).map { "private-message-$suffix-$it" }
        sendActivityMessages(viewer, roomId, messages, privateRoom = true)
        val scenario = ActivityScenario(viewer, roomId, roomName, messages.last(), null)
        retained = true
        return scenario
    } finally {
        if (!retained) viewer.sessionManager.client.close()
    }
}

fun testRoomActivityRefreshByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { ports -> AppiumTestSetup(prepareActivityScenario { target.createAuthenticatedSession(ports) }) },
        ) { scope, data ->
            try {
                val driver = scope.driver
                scenarioSignIn(driver, data.viewer.session.pemPrivateKey)
                driver.dismissDialog()
                driver.clickByText("Rooms")
                driver.assertVisibleByTextContaining(data.latestMessage)
                driver.assertVisibleByText("3")
                val activity =
                    data.viewer.sessionManager.getUserRooms(
                        PaginationQuery(),
                    ).getOrThrow().activities.single { it.room.id == data.roomId }
                data.viewer.sessionManager.addReadLog(
                    UpdateUserRead(ObjectTuple(data.roomId, ObjectType.ROOM), requireNotNull(activity.latestMessageId)),
                ).getOrThrow()
                driver.refreshList()
                driver.assertVisibleByTextContaining(data.latestMessage)
                driver.assertNotVisibleByText("3")
                if (driver is AndroidAppTestDriver) {
                    driver.assertUnreadRoomsIndicator(false)
                } else if (driver is WasmAppTestDriver) {
                    driver.assertUnreadRoomsIndicator(false)
                } else {
                    driver.assertNotVisibleByText("Unread rooms")
                }
            } finally {
                data.viewer.sessionManager.client.close()
            }
        }
    }

private fun AppTestDriver.resizeActivityViewport(width: Int, height: Int) {
    when (this) {
        is DesktopAppTestDriver -> resizeWindow(width, height)
        is WasmAppTestDriver -> resizeViewport(width, height)
        else -> error("The current test platform does not support window resizing")
    }
}

private fun AppTestDriver.assertActivityUnreadIndicator(visible: Boolean) {
    when (this) {
        is AndroidAppTestDriver -> assertUnreadRoomsIndicator(visible)
        is WasmAppTestDriver -> assertUnreadRoomsIndicator(visible)
    }
}

private suspend fun scenarioResizeActivityRooms(driver: AppTestDriver, data: ActivityScenario, compactWidth: Int) {
    if (driver is DesktopAppTestDriver || driver is WasmAppTestDriver) {
        val viewports =
            listOf(
                compactWidth to 800,
                900 to 800,
                1600 to 1000,
                1600 to 500,
                compactWidth to 800,
                1600 to 1000,
            )
        viewports.forEach { (width, height) ->
            driver.resizeActivityViewport(width, height)
            if (width >= 900) driver.assertVisibleByText("Members") else driver.assertNotVisibleByText("Members")
            driver.assertVisibleByTextContaining(data.latestMessage)
            driver.assertVisibleByText("3")
            if (driver is WasmAppTestDriver) driver.assertUnreadRoomsIndicator(true)
            driver.saveSnapshot("activity-rooms-$width-$height")
        }
    }
}

private suspend fun scenarioRevisitActivityRoom(driver: AppTestDriver, roomName: String, latestMessage: String) {
    repeat(3) {
        driver.clickByTextContaining(roomName)
        driver.assertVisibleByDescription("Send")
        driver.assertVisibleByText(latestMessage)
        driver.navigateBack()
        driver.assertVisibleByTextContaining(latestMessage)
        driver.assertActivityUnreadIndicator(false)
    }
}
