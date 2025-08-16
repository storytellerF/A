package com.storyteller_f.a.built_in_bot

import com.google.genai.Client
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createNewTopic
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.getTopicList
import com.storyteller_f.a.client.core.searchCommunity
import com.storyteller_f.a.client.core.signUpOrInFromPrivateKey
import com.storyteller_f.a.client.core.start
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main() {
    Napier.base(kmpLogger)
    loadCryptoLibIfNeed()
    val botPem = System.getenv("BOT_PEM") ?: throw Exception("BOT_PEM not exists")
    val httpUrl = System.getenv("SERVER_URL") ?: throw Exception("SERVER_URL not exists")
    val wsUrl = System.getenv("WS_SERVER_URL") ?: throw Exception("WS_SERVER_URL not exists")
    val pem = botPem.decodeBase64String()
    val sessionManager =
        createUserSessionManager(buildWebSocketUrl(wsUrl), { model, cookieManager ->
            getClient {
                defaultClientConfigure(
                    cookieManager,
                    manager = model,
                    httpUrl = httpUrl,
                    logLevel = LogLevel.INFO
                )
            }
        }) { r, t ->
        }
    val prompt = ClassLoader.getSystemClassLoader().getResourceAsStream("prompt")!!.bufferedReader()
        .readText()
    val client = Client()
    runBlocking {
        val job = launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute ${now()}"
                }
                try {
                    sessionManager.signUpOrInFromPrivateKey(pem, false) {
                        RawUserPass(it)
                    }
                    sessionManager.start {
                        process(sessionManager, client, prompt)
                    }
                } catch (e: Exception) {
                    Napier.e(e) {
                        "process failed"
                    }
                    if (e.message?.contains("User location is not supported for the API use") == true) {
                        break
                    }
                }
                delay(10.seconds)
            }
        }
        // 注册 JVM 关闭钩子，捕获 SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(Thread {
            println("🔻 收到终止信号，准备退出...")
            job.cancel()
            Thread.sleep(1000)
        })
        job.join()
        Napier.i("bot done")
    }
}

private suspend fun process(
    sessionManager: UserSessionManager,
    client: Client,
    prompt: String
) {
    var next: String? = null
    while (true) {
        val resp = sessionManager.searchCommunity(
            10,
            JoinStatusSearch.JOINED,
            nextCommunityId = next
        ).getOrThrow()
        delay(1.seconds)
        resp.data.forEach { communityInfo ->
            Napier.i {
                "check community ${communityInfo.aid} ${communityInfo.name}"
            }
            handle(sessionManager, communityInfo, client, prompt)
        }
        next = resp.pagination?.nextPageToken ?: break
    }
}

private suspend fun handle(
    sessionManager: UserSessionManager,
    info: CommunityInfo,
    client: Client,
    prompt: String
) {
    var next: String? = null
    // 找出最新的评论过的topic
    var hasCommentId = 0L
    while (true) {
        val resp = sessionManager.getTopicList(
            ObjectType.COMMUNITY,
            info.id,
            TopicPinSearch.UNSPECIFIED,
            PaginationQuery(next, null, size = 10)
        ).getOrThrow()
        delay(1.seconds)
        for (topicInfo in resp.data) {
            if (topicInfo.hasComment) {
                hasCommentId = topicInfo.id
                break
            }
        }
        if (hasCommentId != 0L) break
        next = resp.pagination?.nextPageToken ?: break
    }
    Napier.i {
        "latest comment topic is $hasCommentId"
    }
    var pre = hasCommentId.toString()
    while (true) {
        val resp = sessionManager.getTopicList(
            ObjectType.COMMUNITY,
            info.id,
            TopicPinSearch.UNSPECIFIED,
            PaginationQuery(null, pre, size = 10)
        ).getOrThrow()
        delay(1.seconds)
        resp.data.forEach { topicInfo ->
            Napier.i {
                "handle ${topicInfo.id} ${topicInfo.hasComment}"
            }
            handleTopic(topicInfo, client, sessionManager, prompt)
            delay(1.seconds)
        }
        pre = resp.pagination?.prePageToken ?: break
    }
}

private suspend fun handleTopic(
    topicInfo: TopicInfo,
    client: Client,
    sessionManager: UserSessionManager,
    prompt: String
) {
    if (topicInfo.author != sessionManager.sessionModel.uid && !topicInfo.hasComment) {
        return
    }

    val plain = (topicInfo.content as TopicContent.Plain).plain
    val text = if (plain.length < 10) {
        null
    } else {
        val response = client.models.generateContent(
            "gemini-2.5-flash",
            "$prompt\n${topicInfo.content}",
            null
        )
        response.text()?.take(1000)
    } ?: "👍"
    sessionManager.createNewTopic(ObjectType.TOPIC, topicInfo.id, text)
        .onSuccess {
            Napier.i {
                "createTopic success $text"
            }
        }.onFailure {
            Napier.e(it.cause) {
                "createNewTopic failed"
            }
        }
    delay(1.seconds)
}
