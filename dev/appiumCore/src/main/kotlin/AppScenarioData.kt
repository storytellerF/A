import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

data class SubscriptionTopicScenario(
    val authenticated: AuthenticatedSession,
    val topicId: Long,
    val communityName: String,
)

suspend fun prepareSubscriptionTopicScenario(
    now: Long,
    topicContent: String,
    createAuthenticatedSession: suspend () -> AuthenticatedSession,
): SubscriptionTopicScenario {
    val owner = createAuthenticatedSession()
    var viewer: AuthenticatedSession? = null
    try {
        val aidSuffix = (now % 1_000_000).toString().padStart(6, '0')
        val communityName = "community-$aidSuffix"
        val communityId = owner.sessionManager.createCommunity(
            NewCommunity(communityName, "sc$aidSuffix")
        ).getOrThrow().id
        val topicId = owner.sessionManager.createTopic(
            ObjectType.COMMUNITY,
            communityId,
            topicContent
        ).getOrThrow().id
        viewer = createAuthenticatedSession()
        viewer.sessionManager.joinCommunity(communityId).getOrThrow()
        return SubscriptionTopicScenario(viewer, topicId, communityName)
    } catch (throwable: Throwable) {
        viewer?.sessionManager?.client?.close()
        throw throwable
    } finally {
        owner.sessionManager.client.close()
    }
}

suspend fun waitUntilTopicSubscribed(
    sessionManager: com.storyteller_f.a.client.core.UserSessionManager,
    topicId: Long,
    timeoutMillis: Long = java.time.Duration.ofSeconds(30).toMillis(),
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (sessionManager.getTopicInfo(topicId).getOrThrow().subscriptionId != null) return
        delay(500.milliseconds)
    }
    error("Topic not marked as subscribed: $topicId")
}
