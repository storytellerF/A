package com.storyteller_f.a.panel_cli_app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.runMosaicBlocking
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.text.withStyle
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.createPanelSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getAllCommunities
import com.storyteller_f.a.client.core.getAllPublicRooms
import com.storyteller_f.a.client.core.getAllUsers
import com.storyteller_f.a.client.core.getPanelUserPass
import com.storyteller_f.a.client.core.overview
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel

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
    data class UserList(val users: List<UserInfo>) : Screen()
    data class CommunityList(val communities: List<CommunityInfo>) : Screen()
    data class RoomList(val rooms: List<RoomInfo>) : Screen()
    data class Overview(val state: PanelOverview) : Screen()
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
suspend fun handleInput(
    line: String,
    screen: Screen,
    setScreen: (Screen) -> Unit,
    sessionManager: PanelSessionManager
) {
    when (screen) {
        is Screen.Main -> {
            when (line) {
                "1" -> setScreen(Screen.PromptRegister)
                "2" -> setScreen(Screen.PromptLogin)
                "3" -> {
                    sysLog("Fetching Users...")
                    sessionManager.getAllUsers(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.UserList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "4" -> {
                    sysLog("Fetching Communities...")
                    sessionManager.getAllCommunities(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.CommunityList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "5" -> {
                    sysLog("Fetching Rooms...")
                    sessionManager.getAllPublicRooms(PaginationQuery(size = 10)).fold(
                        onSuccess = { setScreen(Screen.RoomList(it.data)) },
                        onFailure = { sysLogError("Failed", it) }
                    )
                }
                "6" -> {
                    sysLog("Fetching Overview...")
                    sessionManager.overview().fold(
                        onSuccess = { setScreen(Screen.Overview(it)) },
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
                    val algo = getAlgo(AlgoType.P256)
                    val derPriKey = algo.getDerPrivateKey(pk).getOrThrow()
                    val derPubKey = algo.getDerPublicKeyFromPrivateKey(pk).getOrThrow()
                    val user = sessionManager.getPanelUserPass(
                        AuthKey.P256(pk, derPriKey, derPubKey),
                        true
                    ) { RawUserPass(it) }
                    sysLog("Registered and Logged in as: ${user.name}")
                    setScreen(Screen.Main)
                } catch (e: Exception) {
                    sysLogError("Error", e)
                    setScreen(Screen.Main)
                }
            }
        }
        is Screen.PromptLogin -> {
            try {
                val algo = getAlgo(AlgoType.P256)
                val derPriKey = algo.getDerPrivateKey(line).getOrThrow()
                val derPubKey = algo.getDerPublicKeyFromPrivateKey(line).getOrThrow()
                val user = sessionManager.getPanelUserPass(
                    AuthKey.P256(line, derPriKey, derPubKey),
                    false
                ) { RawUserPass(it) }
                sysLog("Logged in as: ${user.name}")
                setScreen(Screen.Main)
            } catch (e: Exception) {
                sysLogError("Error", e)
                setScreen(Screen.Main)
            }
        }
        is Screen.UserList -> {
            if (line == "") {
                setScreen(Screen.Main)
            }
        }
        is Screen.CommunityList -> {
            if (line == "") {
                setScreen(Screen.Main)
            }
        }
        is Screen.RoomList -> {
            if (line == "") {
                setScreen(Screen.Main)
            }
        }
        is Screen.Overview -> {
            if (line == "") {
                setScreen(Screen.Main)
            }
        }
    }
}

@Suppress("ComplexMethod", "LongMethod")
@Composable
fun App(sessionManager: PanelSessionManager) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var inputBuffer by remember { mutableStateOf("") }
    val submitChannel = remember { Channel<String>(Channel.UNLIMITED) }

    LaunchedEffect(Unit) {
        for (line in submitChannel) {
            handleInput(line, screen, { screen = it }, sessionManager)
        }
    }

    Column(modifier = Modifier.onKeyEvent { event ->
        if (event.key == "Enter") {
            submitChannel.trySend(inputBuffer)
            inputBuffer = ""
        } else if (event.key == "Backspace") {
            if (inputBuffer.isNotEmpty()) inputBuffer = inputBuffer.dropLast(1)
        } else if (event.key == "Space") {
            inputBuffer += " "
        } else if (event.key.length == 1) {
            inputBuffer += event.key
        }
        true
    }) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TitleGreen, textStyle = TextStyle.Bold)) {
                    append("PANEL CLI")
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
                Text("3. Get All Users")
                Text("4. Get All Communities")
                Text("5. Get All Public Rooms")
                Text("6. Get Overview")
                Text("0. Exit")
                Text("\nChoice: ")
            }
            is Screen.PromptRegister -> {
                Text("Enter Private Key (leave empty for auto-generate): ")
            }
            is Screen.PromptLogin -> {
                Text("Enter Private Key: ")
            }
            is Screen.UserList -> {
                Text("=== Member List ===")
                s.users.forEachIndexed { i, u ->
                    Text("[$i] User ID: ${u.id} - ${u.nickname}")
                }
                Text("===================")
                Text("\nHit enter to go back: ")
            }
            is Screen.CommunityList -> {
                Text("=== Community List ===")
                s.communities.forEachIndexed { i, c ->
                    Text("[$i] Community ID: ${c.id} - ${c.name}")
                }
                Text("======================")
                Text("\nHit enter to go back: ")
            }
            is Screen.RoomList -> {
                Text("=== Room List ===")
                s.rooms.forEachIndexed { i, r ->
                    Text("[$i] Room ID: ${r.id} - ${r.name}")
                }
                Text("=================")
                Text("\nHit enter to go back: ")
            }
            is Screen.Overview -> {
                Text("=== Overview ===")
                Text("User count: ${s.state.userCount}")
                Text("Topic count: ${s.state.topicCount}")
                Text("Community count: ${s.state.communityCount}")
                Text("Private room count: ${s.state.privateRoomCount}")
                Text("Community room count: ${s.state.communityRoomCount}")
                Text("File count: ${s.state.fileCount}")
                Text("File volume: ${s.state.fileVolume}")
                Text("Title count: ${s.state.titleCount}")
                Text("=================")
                Text("\nHit enter to go back: ")
            }
        }

        Text("")
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = TitleGreen)) {
                append("> ")
            }
            append(inputBuffer)
            append("\u2588")
        })
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

    val httpUrl = "http://127.0.0.1:8080"

    val sessionManager = createPanelSessionManager { model, cookieManager ->
        HttpClient(OkHttp) {
            defaultClientConfigureForPanel(cookieManager, manager = model, httpUrl = httpUrl)
        }
    }

    val jobs = run {
        sessionManager.startBackgroundTask()
    }

    runMosaicBlocking {
        App(sessionManager)
    }

    jobs.forEach { it.cancel() }
}
