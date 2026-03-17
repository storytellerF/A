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
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getRecommendTopics
import com.storyteller_f.a.client.core.getUserCommunities
import com.storyteller_f.a.client.core.getUserPass
import com.storyteller_f.a.client.core.getUserRooms
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
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
    data class RoomList(val rooms: List<RoomInfo>) : Screen()
    data class RoomDetail(val room: RoomInfo) : Screen()
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
        is Screen.TopicDetail -> { setScreen(Screen.Main) }
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
        is Screen.CommunityDetail -> { setScreen(Screen.Main) }
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
        is Screen.RoomDetail -> { setScreen(Screen.Main) }
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
                Text("Recommended Topics:")
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
                Text("Hit enter to go back.")
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
