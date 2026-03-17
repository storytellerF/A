package com.storyteller_f.a.cli_app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.runMosaicBlocking
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.text.withStyle
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.addFavorite
import com.storyteller_f.a.client.core.addSubscription
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.exitCommunity
import com.storyteller_f.a.client.core.exitRoom
import com.storyteller_f.a.client.core.getCommunityRooms
import com.storyteller_f.a.client.core.getCommunityTopics
import com.storyteller_f.a.client.core.getFavorites
import com.storyteller_f.a.client.core.getRecommendTopics
import com.storyteller_f.a.client.core.getRoomMembersPublicKeys
import com.storyteller_f.a.client.core.getRoomTopics
import com.storyteller_f.a.client.core.getSubscriptions
import com.storyteller_f.a.client.core.getUserCommunities
import com.storyteller_f.a.client.core.getUserPass
import com.storyteller_f.a.client.core.getUserRooms
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.searchAllMembers
import com.storyteller_f.a.client.core.searchCommunityMembers
import com.storyteller_f.a.client.core.searchRoomMembers
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Scanner

private val LogGray = Color(160, 160, 160)
private val TitleGreen = Color(100, 255, 100)

val logs = mutableStateListOf<String>()

class ConsoleAntilog : Antilog() {
    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        if (message != null) {
            val prefix = tag?.let { "[$it] " } ?: ""
            logs.add("$priority $prefix$message")
            if (logs.size > 15) logs.removeAt(0)
        }
        throwable?.let {
            logs.add("$priority ${it.message}")
            if (logs.size > 15) logs.removeAt(0)
        }
    }
}

fun sysLog(s: String) {
    Napier.i(s)
}

fun sysLogError(s: String, t: Throwable? = null) {
    if (t != null) {
        Napier.e(s, t)
    } else {
        Napier.e(s)
    }
}

sealed class Screen {
    data object Main : Screen()
    data object PromptRegister : Screen()
    data object PromptLogin : Screen()
    data class TopicList(val topics: List<TopicInfo>) : Screen()
    data class TopicDetail(val topic: TopicInfo) : Screen()
    data class CommunityList(val communities: List<CommunityInfo>) : Screen()
    data class CommunityDetail(val community: CommunityInfo) : Screen()
    data class CommunityMemberList(val members: List<MemberInfo>, val parent: Screen) : Screen()
    data class RoomMemberList(val members: List<MemberInfo>, val parent: Screen) : Screen()
    data class UserList(val users: List<UserInfo>) : Screen()
    data class UserDetail(val user: UserInfo, val parent: Screen) : Screen()
    data class RoomList(val rooms: List<RoomInfo>) : Screen()
    data class RoomDetail(val room: RoomInfo) : Screen()
    data class RoomSendMessage(val room: RoomInfo) : Screen()
    data class FavoriteList(val favorites: List<UserFavoriteInfo>) : Screen()
    data class SubscriptionList(val subscriptions: List<UserSubscriptionInfo>) : Screen()
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
suspend fun handleInput(
    line: String,
    screen: Screen,
    setScreen: (Screen) -> Unit,
    sessionManager: UserSessionManager
) {
    when (screen) {
        is Screen.Main -> {
            when (line) {
                "1" -> setScreen(Screen.PromptRegister)
                "2" -> setScreen(Screen.PromptLogin)
                "3" -> {
                    sysLog("Fetching Recommended Topics...")
                    sessionManager.getRecommendTopics(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.TopicList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "4" -> {
                    sysLog("Fetching Joined Communities...")
                    sessionManager.getUserCommunities(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.CommunityList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "5" -> {
                    sysLog("Fetching Joined Rooms...")
                    sessionManager.getUserRooms(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.RoomList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "6" -> {
                    sysLog("Fetching Member List...")
                    sessionManager.searchAllMembers(null, 10, "").fold(
                        onSuccess = { setScreen(Screen.UserList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "7" -> {
                    sysLog("Fetching Favorites...")
                    sessionManager.getFavorites(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.FavoriteList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "8" -> {
                    sysLog("Fetching Subscriptions...")
                    sessionManager.getSubscriptions(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.SubscriptionList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "0" -> kotlin.system.exitProcess(0)
                else -> sysLog("Invalid Choice")
            }
        }
        is Screen.PromptRegister -> {
            var pk = line
            if (pk.isEmpty()) {
                val algo = getAlgo(AlgoType.P256)
                pk = algo.generatePemKeyPair().getOrNull()?.first ?: ""
                sysLog("Generated Private Key: $pk")
            }
            if (pk.isNotEmpty()) {
                try {
                    val user = sessionManager.getUserPass(pk, AlgoType.P256, true) { RawUserPass(it) }
                    sysLog("Registered and Logged in as: ${user.id} - ${user.nickname}")
                    setScreen(Screen.Main)
                } catch (e: Exception) {
                    sysLogError("Error", e)
                    setScreen(Screen.Main)
                }
            }
        }
        is Screen.PromptLogin -> {
            try {
                val user = sessionManager.getUserPass(line, AlgoType.P256, false) { RawUserPass(it) }
                sysLog("Logged in as: ${user.id} - ${user.nickname}")
                setScreen(Screen.Main)
            } catch (e: Exception) {
                sysLogError("Error", e)
                setScreen(Screen.Main)
            }
        }
        is Screen.TopicList -> {
            if (line == "") {
                setScreen(Screen.Main)
            } else {
                val idx = line.toIntOrNull()
                if (idx != null && idx in screen.topics.indices) {
                    setScreen(Screen.TopicDetail(screen.topics[idx]))
                }
            }
        }
        is Screen.TopicDetail -> {
            when (line) {
                "1" -> {
                    sysLog("Favoriting Topic...")
                    sessionManager.addFavorite(NewFavorite(ObjectType.TOPIC, screen.topic.id)).fold(
                        onSuccess = { sysLog("Favorited successfully") },
                        onFailure = { sysLogError("Failed to favorite", it) }
                    )
                }
                "2" -> {
                    sysLog("Subscribing Topic...")
                    sessionManager.addSubscription(NewSubscription(screen.topic.id, ObjectType.TOPIC)).fold(
                        onSuccess = { sysLog("Subscribed successfully") },
                        onFailure = { sysLogError("Failed to subscribe", it) }
                    )
                }
                "" -> setScreen(Screen.Main)
                else -> sysLog("Invalid Choice")
            }
        }
        is Screen.CommunityList -> {
            if (line == "") {
                setScreen(Screen.Main)
            } else {
                val idx = line.toIntOrNull()
                if (idx != null && idx in screen.communities.indices) {
                    setScreen(Screen.CommunityDetail(screen.communities[idx]))
                }
            }
        }
        is Screen.CommunityDetail -> {
            when (line) {
                "1" -> {
                    sysLog("Fetching Community Topics...")
                    sessionManager.getCommunityTopics(screen.community.id, null, PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.TopicList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "2" -> {
                    sysLog("Fetching Community Rooms...")
                    sessionManager.getCommunityRooms(
                        screen.community.id,
                        CustomApi.Communities.Id.Rooms.CommunityRoomQuery(size = 10)
                    ).fold(
                        onSuccess = { setScreen(Screen.RoomList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "3" -> {
                    sysLog("Fetching Community Members...")
                    sessionManager.searchCommunityMembers(screen.community.id, null, 10, "").fold(
                        onSuccess = { setScreen(Screen.CommunityMemberList(it.data, screen)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "4" -> {
                    sysLog("Joining Community...")
                    sessionManager.joinCommunity(screen.community.id).fold(
                        onSuccess = { sysLog("Joined Community successfully") },
                        onFailure = { sysLogError("Failed to join", it) }
                    )
                }
                "5" -> {
                    sysLog("Exiting Community...")
                    sessionManager.exitCommunity(screen.community.id).fold(
                        onSuccess = { sysLog("Exited Community successfully") },
                        onFailure = { sysLogError("Failed to exit", it) }
                    )
                }
                "6" -> {
                    sysLog("Favoriting Community...")
                    sessionManager.addFavorite(NewFavorite(ObjectType.COMMUNITY, screen.community.id)).fold(
                        onSuccess = { sysLog("Favorited successfully") },
                        onFailure = { sysLogError("Failed to favorite", it) }
                    )
                }
                "7" -> {
                    sysLog("Subscribing Community...")
                    sessionManager.addSubscription(NewSubscription(screen.community.id, ObjectType.COMMUNITY)).fold(
                        onSuccess = { sysLog("Subscribed successfully") },
                        onFailure = { sysLogError("Failed to subscribe", it) }
                    )
                }
                "" -> setScreen(Screen.Main)
                else -> sysLog("Invalid Choice")
            }
        }
        is Screen.CommunityMemberList -> {
            if (line == "") {
                setScreen(screen.parent)
            } else {
                val idx = line.toIntOrNull()
                if (idx != null && idx in screen.members.indices) {
                    setScreen(Screen.UserDetail(screen.members[idx].userInfo, screen))
                }
            }
        }
        is Screen.RoomMemberList -> {
            if (line == "") {
                setScreen(screen.parent)
            } else {
                val idx = line.toIntOrNull()
                if (idx != null && idx in screen.members.indices) {
                    setScreen(Screen.UserDetail(screen.members[idx].userInfo, screen))
                }
            }
        }
        is Screen.UserList -> {
            if (line == "") {
                setScreen(Screen.Main)
            } else {
                val idx = line.toIntOrNull()
                if (idx != null && idx in screen.users.indices) {
                    setScreen(Screen.UserDetail(screen.users[idx], screen))
                }
            }
        }
        is Screen.UserDetail -> { setScreen(screen.parent) }
        is Screen.RoomList -> {
            if (line == "") {
                setScreen(Screen.Main)
            } else {
                val idx = line.toIntOrNull()
                if (idx != null && idx in screen.rooms.indices) {
                    setScreen(Screen.RoomDetail(screen.rooms[idx]))
                }
            }
        }
        is Screen.RoomDetail -> {
            when (line) {
                "1" -> {
                    sysLog("Fetching Room Messages...")
                    sessionManager.getRoomTopics(screen.room.id, null, PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.TopicList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "2" -> {
                    sysLog("Fetching Room Members...")
                    sessionManager.searchRoomMembers(screen.room.id, null, 10, "").fold(
                        onSuccess = { setScreen(Screen.RoomMemberList(it.data, screen)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "3" -> {
                    sysLog("Joining Room...")
                    sessionManager.joinRoom(screen.room.id).fold(
                        onSuccess = { sysLog("Joined Room successfully") },
                        onFailure = { sysLogError("Failed to join", it) }
                    )
                }
                "4" -> {
                    sysLog("Exiting Room...")
                    sessionManager.exitRoom(screen.room.id).fold(
                        onSuccess = { sysLog("Exited Room successfully") },
                        onFailure = { sysLogError("Failed to exit", it) }
                    )
                }
                "5" -> {
                    setScreen(Screen.RoomSendMessage(screen.room))
                }
                "6" -> {
                    sysLog("Favoriting Room...")
                    sessionManager.addFavorite(NewFavorite(ObjectType.ROOM, screen.room.id)).fold(
                        onSuccess = { sysLog("Favorited successfully") },
                        onFailure = { sysLogError("Failed to favorite", it) }
                    )
                }
                "7" -> {
                    sysLog("Subscribing Room...")
                    sessionManager.addSubscription(NewSubscription(screen.room.id, ObjectType.ROOM)).fold(
                        onSuccess = { sysLog("Subscribed successfully") },
                        onFailure = { sysLogError("Failed to subscribe", it) }
                    )
                }
                "" -> setScreen(Screen.Main)
                else -> sysLog("Invalid Choice")
            }
        }
        is Screen.FavoriteList -> {
            if (line == "") {
                setScreen(Screen.Main)
            }
        }
        is Screen.SubscriptionList -> {
            if (line == "") {
                setScreen(Screen.Main)
            }
        }
        is Screen.RoomSendMessage -> {
            if (line.isEmpty()) {
                setScreen(Screen.RoomDetail(screen.room))
            } else {
                sysLog("Sending Message to Room...")
                val parentTarget = ObjectTuple(screen.room.id, ObjectType.ROOM)

                var keyData: List<com.storyteller_f.shared.model.UserPubKeyInfo>? = null
                if (screen.room.isPrivate) {
                    val res = sessionManager.getRoomMembersPublicKeys(screen.room.id, PaginationQuery(size = 100))
                    if (res.isSuccess) {
                        keyData = res.getOrNull()?.data
                    }
                }

                try {
                    sessionManager.webSocketClient.useWebSocket {
                        sendMessage(parentTarget, screen.room.isPrivate, line, keyData.orEmpty())
                    }
                    sysLog("Message Sent")
                } catch (e: Exception) {
                    sysLogError("Failed to send message", e)
                }
                setScreen(Screen.RoomDetail(screen.room))
            }
        }
    }
}

@Suppress("ComplexMethod", "LongMethod")
@Composable
fun App(sessionManager: UserSessionManager) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val scanner = Scanner(System.`in`)
            while (isActive) {
                if (scanner.hasNextLine()) {
                    val line = scanner.nextLine()
                    handleInput(line, screen, { screen = it }, sessionManager)
                } else {
                    delay(100L)
                }
            }
        }
    }

    Column {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TitleGreen, textStyle = TextStyle.Bold)) {
                    append("STORYTELLER CLI")
                }
            }
        )
        Text("===============")
        Text("")
        when (val s = screen) {
            is Screen.Main -> {
                Text("--- Menu ---")
                Text("1. Register")
                Text("2. Login")
                Text("3. Recommend Topics")
                Text("4. Joined Communities")
                Text("5. Joined Rooms")
                Text("6. Member List")
                Text("7. My Favorites")
                Text("8. My Subscriptions")
                Text("0. Exit")
                Text("\nChoice: ")
            }
            is Screen.PromptRegister -> {
                Text("Enter Private Key (leave empty for auto-generate): ")
            }
            is Screen.PromptLogin -> {
                Text("Enter Private Key: ")
            }
            is Screen.TopicList -> {
                Text("Topic/Message List:")
                s.topics.forEachIndexed { i, t ->
                    val content = t.content
                    val title = if (content is TopicContent.Plain) content.plain else "Encrypted"
                    Text("[$i] ${t.id}: $title")
                }
                Text("\nEnter index to view details (or hit enter to go back): ")
            }
            is Screen.TopicDetail -> {
                Text("=== Topic Detail ===")
                Text("ID: ${s.topic.id}")
                Text("Root ID: ${s.topic.rootId}")
                Text("Author: ${s.topic.extension?.authorInfo?.nickname}")
                Text("Content: ${s.topic.content}")
                Text("====================")
                Text("1. Favorite Topic")
                Text("2. Subscribe Topic")
                Text("")
                Text("Hit enter to go back.")
            }
            is Screen.CommunityList -> {
                Text("Joined Communities:")
                s.communities.forEachIndexed { i, c ->
                    Text("[$i] ${c.id}: ${c.name}")
                }
                Text("\nEnter index to view details (or hit enter to go back): ")
            }
            is Screen.CommunityDetail -> {
                Text("=== Community Detail ===")
                Text("ID: ${s.community.id}")
                Text("Name: ${s.community.name}")
                Text("Member Count: ${s.community.memberCount}")
                Text("========================")
                Text("1. View Topics")
                Text("2. View Rooms")
                Text("3. View Members")
                Text("4. Join Community")
                Text("5. Exit Community")
                Text("6. Favorite Community")
                Text("7. Subscribe Community")
                Text("")
                Text("Enter choice (or hit enter to go back): ")
            }
            is Screen.CommunityMemberList -> {
                Text("=== Community Members ===")
                s.members.forEachIndexed { i, m ->
                    Text("[$i] User ID: ${m.uid} - ${m.userInfo.nickname}")
                }
                Text("========================")
                Text("\nEnter index to view user details (or hit enter to go back): ")
            }
            is Screen.RoomMemberList -> {
                Text("=== Room Members ===")
                s.members.forEachIndexed { i, m ->
                    Text("[$i] User ID: ${m.uid} - ${m.userInfo.nickname}")
                }
                Text("==================")
                Text("\nEnter index to view user details (or hit enter to go back): ")
            }
            is Screen.UserList -> {
                Text("=== Member List ===")
                s.users.forEachIndexed { i, u ->
                    Text("[$i] User ID: ${u.id} - ${u.nickname}")
                }
                Text("===================")
                Text("\nEnter index to view user details (or hit enter to go back): ")
            }
            is Screen.UserDetail -> {
                Text("=== User Detail ===")
                Text("ID: ${s.user.id}")
                Text("Nickname: ${s.user.nickname}")
                Text("Address: ${s.user.address}")
                Text("Aid: ${s.user.aid ?: "None"}")
                Text("Status: ${s.user.status}")
                Text("===================")
                Text("Hit enter to go back.")
            }
            is Screen.RoomList -> {
                Text("Joined Rooms:")
                s.rooms.forEachIndexed { i, r ->
                    Text("[$i] ${r.id}: ${r.name}")
                }
                Text("\nEnter index to view details (or hit enter to go back): ")
            }
            is Screen.RoomDetail -> {
                Text("=== Room Detail ===")
                Text("ID: ${s.room.id}")
                Text("Name: ${s.room.name}")
                Text("Member Count: ${s.room.memberCount}")
                Text("===================")
                Text("1. View Messages")
                Text("2. View Members")
                Text("3. Join Room")
                Text("4. Exit Room")
                Text("5. Send Message")
                Text("6. Favorite Room")
                Text("7. Subscribe Room")
                Text("")
                Text("Enter choice (or hit enter to go back): ")
            }
            is Screen.FavoriteList -> {
                Text("=== Favorites ===")
                s.favorites.forEachIndexed { i, f ->
                    val extra = f.extensions?.topicInfo?.content
                        ?: f.extensions?.communityInfo?.name
                        ?: f.extensions?.roomInfo?.name
                        ?: "Unknown"
                    Text("[$i] ${f.objectType} ${f.objectId}: $extra")
                }
                Text("\nHit enter to go back: ")
            }
            is Screen.SubscriptionList -> {
                Text("=== Subscriptions ===")
                s.subscriptions.forEachIndexed { i, sub ->
                    val extra = sub.extensions?.topicInfo?.content ?: "Unknown"
                    Text("[$i] ${sub.objectType} ${sub.objectId}: $extra")
                }
                Text("\nHit enter to go back: ")
            }
            is Screen.RoomSendMessage -> {
                Text("=== Send Message ===")
                Text("Room: ${s.room.name}")
                Text("Type your message and hit Enter.")
                Text("Hit enter with empty input to go back.")
                Text("Message: ")
            }
        }

        Text("")
        Text("--- Logs ---")
        logs.forEach { logText ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = LogGray)) {
                        append(logText)
                    }
                }
            )
        }
    }
}

fun main() = kotlinx.coroutines.runBlocking {
    Napier.base(ConsoleAntilog())

    val wsUrl = "ws://127.0.0.1:8080"
    val httpUrl = "http://127.0.0.1:8080"

    val sessionManager = createUserSessionManager(wsUrl, { model, cookieManager ->
        HttpClient(OkHttp) {
            defaultClientConfigure(cookieManager, manager = model, httpUrl = httpUrl)
        }
    }, { _, _, _ -> })

    val jobs = run {
        sessionManager.startBackgroundTask()
    }

    runMosaicBlocking {
        App(sessionManager)
    }

    jobs.forEach { it.cancel() }
}
