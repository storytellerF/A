package com.storyteller_f.a.built_in_bot

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GoogleSearch
import com.google.genai.types.Tool
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.SessionManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

fun main() {
    Napier.base(kmpLogger)
    loadCryptoLibIfNeed()
    val base64BotPem = System.getenv("BOT_PEM") ?: throw Exception("BOT_PEM not exists")
    val httpUrl = System.getenv("SERVER_URL") ?: throw Exception("SERVER_URL not exists")
    val wsUrl = System.getenv("WS_SERVER_URL") ?: throw Exception("WS_SERVER_URL not exists")
    val pemPrivateKey = base64BotPem.decodeBase64String()
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
    val classLoader = ClassLoader.getSystemClassLoader()
    val commentPrompt = readResource(classLoader, "comment.prompt")
    val newsPrompt = readResource(classLoader, "news.prompt")
    val client = Client()
    runBlocking {
        val job = launch {
            // 确保第一次登录成功
            while (isActive) {
                try {
                    sessionManager.signUpOrInFromPrivateKey(pemPrivateKey, false) {
                        RawUserPass(it)
                    }
                    break
                } catch (e: Exception) {
                    Napier.e(e) {
                        "login failed"
                    }
                }
            }
            processJob(sessionManager, client, commentPrompt, newsPrompt)
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

private suspend fun CoroutineScope.processJob(
    sessionManager: UserSessionManager,
    client: Client,
    commentPrompt: String,
    newsPrompt: String
) {
    val jobs = sessionManager.start()
    val job1 = launch {
        loop(1.minutes) {
            processCommunityTask(sessionManager) { communityInfo ->
                handleCommunityComment(
                    sessionManager,
                    client,
                    communityInfo,
                    commentPrompt
                )
            }
        }
    }
    val job2 = launch {
        loop(1.hours) {
            processCommunityTask(sessionManager) { communityInfo ->
                handleCommunityNews(sessionManager, client, communityInfo, newsPrompt)
            }
        }
    }
    job1.join()
    job2.join()
    jobs.forEach {
        it.cancel()
    }
}

private suspend fun CoroutineScope.loop(duration: Duration, block: suspend () -> Unit) {
    while (isActive) {
        try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            } else if (e.message?.contains("User location is not supported for the API use") == true) {
                break
            } else {
                Napier.e(e) {
                    "handleCommunityNews failed"
                }
            }
        }
        delay(duration)
    }
}

private fun readResource(classLoader: ClassLoader?, name: String): String =
    classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

private suspend fun processCommunityTask(
    sessionManager: UserSessionManager,
    extracted: suspend (CommunityInfo) -> Unit
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
            extracted(communityInfo)
        }
        next = resp.pagination?.nextPageToken ?: break
    }
}

private suspend fun handleCommunityComment(
    sessionManager: UserSessionManager,
    client: Client,
    info: CommunityInfo,
    prompt: String
) {
    Napier.i {
        "check community latest commented topic ${info.name}[${info.aid}]"
    }
    var pre = getLatestHasCommentedTopic(sessionManager, info).toString()
    while (true) {
        val resp = sessionManager.getTopicList(
            ObjectType.COMMUNITY,
            info.id,
            TopicPinSearch.UNSPECIFIED,
            PaginationQuery(null, pre, size = 10)
        ).getOrThrow()
        delay(1.seconds)
        resp.data.forEach { topicInfo ->
            val isAuthor = topicInfo.author == sessionManager.sessionModel.uid
            if (isAuthor || topicInfo.hasComment) {
                Napier.i {
                    "skip topic ${topicInfo.id} " +
                        "isAuthor: $isAuthor " +
                        "hasComment: ${topicInfo.hasComment} " +
                        "createdTime: ${topicInfo.createdTime}"
                }
            } else {
                Napier.i {
                    "handle ${topicInfo.id} createdTime: ${topicInfo.createdTime}"
                }
                handleTopic(topicInfo, client, sessionManager, prompt)
                delay(1.seconds)
            }
        }
        pre = resp.pagination?.prePageToken ?: break
    }
}

private suspend fun getLatestHasCommentedTopic(
    sessionManager: UserSessionManager,
    info: CommunityInfo
): Long {
    var next: String? = null
    // 找出最新的评论过的topic
    var latestHasCommentedTopicId = 0L
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
                latestHasCommentedTopicId = topicInfo.id
                break
            }
        }
        if (latestHasCommentedTopicId != 0L) break
        next = resp.pagination?.nextPageToken ?: break
    }
    Napier.i {
        "latest commented topic is $latestHasCommentedTopicId"
    }
    return latestHasCommentedTopicId
}

private suspend fun handleTopic(
    topicInfo: TopicInfo,
    client: Client,
    sessionManager: UserSessionManager,
    prompt: String
) {
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
                "createNewTopic success $text"
            }
        }.onFailure {
            Napier.e(it.cause) {
                "createNewTopic failed"
            }
        }
    delay(1.seconds)
}

@OptIn(ExperimentalTime::class)
private suspend fun handleCommunityNews(
    sessionManager: SessionManager,
    client: Client,
    communityInfo: CommunityInfo,
    prompt: String
) {
    Napier.i {
        "check community bot created latest topic ${communityInfo.name}[${communityInfo.aid}]"
    }
    var next: String? = null
    // 找出最新的评论过的topic
    var latestTopic: TopicInfo? = null
    while (true) {
        val resp = sessionManager.getTopicList(
            ObjectType.COMMUNITY,
            communityInfo.id,
            TopicPinSearch.UNSPECIFIED,
            PaginationQuery(next, null, size = 10)
        ).getOrThrow()
        delay(1.seconds)
        for (topicInfo in resp.data) {
            if (topicInfo.author == sessionManager.sessionModel.uid) {
                latestTopic = topicInfo
                break
            }
        }
        if (latestTopic != null) break
        next = resp.pagination?.nextPageToken ?: break
    }
    Napier.i {
        "latest bot created topic ${latestTopic?.id}"
    }
    addTopic(latestTopic, client, prompt, communityInfo, sessionManager)
}

@OptIn(ExperimentalTime::class)
private suspend fun addTopic(
    latestTopic: TopicInfo?,
    client: Client,
    prompt: String,
    communityInfo: CommunityInfo,
    sessionManager: SessionManager
) {
    val now = now()
    val previousDate = now.toInstant(TimeZone.UTC).minus(1.days).toLocalDateTime(TimeZone.UTC)
    val year = previousDate.year
    val month = previousDate.month // 1-12
    val previousDay = previousDate.day
    Napier.i {
        "previous day [$year $month $previousDay] and now is $now"
    }
    if (latestTopic != null) {
        Napier.i {
            "latest bot created topic is ${latestTopic.id} createdTime: ${latestTopic.createdTime}"
        }
        val start = latestTopic.createdTime
        val latestYear = start.year
        val latestMonth = start.month
        val latestDay = start.day
        if (latestDay != previousDay + 1 || latestYear != year || latestMonth != month) {
            createNewsTopic(client, prompt, communityInfo, sessionManager, previousDate)
        } else {
            Napier.i {
                "skip create topic"
            }
        }
    } else {
        Napier.i {
            "never created topic"
        }
        createNewsTopic(client, prompt, communityInfo, sessionManager, previousDate)
    }
}

private suspend fun createNewsTopic(
    client: Client,
    newPrompt: String,
    communityInfo: CommunityInfo,
    sessionManager: SessionManager,
    date: LocalDateTime
) {
    val year = date.year
    val month = date.month // 1-12
    val day = date.day
    val response = client.models.generateContent(
        "gemini-2.5-flash",
        "$newPrompt\n日期:${year}年${month}月${day}日，领域:${communityInfo.name}",
        GenerateContentConfig.builder().tools(Tool.builder().googleSearch(GoogleSearch.builder()))
            .build()
    )
    val content = response.text()?.take(1000) ?: "😴"
    sessionManager.createNewTopic(ObjectType.COMMUNITY, communityInfo.id, content).onSuccess {
        Napier.i {
            "create new topic success $date $content"
        }
    }.onFailure {
        Napier.e(it) {
            "create new topic failed $date $content"
        }
    }
    delay(1.seconds)
}
