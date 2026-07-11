import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
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
    val topicContent: String,
)

data class FavoriteTopicScenario(
    val authenticated: AuthenticatedSession,
    val topicId: Long,
    val topicContent: String,
)

data class PreparedCommunityRoomScenario(
    val ownerSession: InjectedSession,
    val viewerSession: InjectedSession,
    val communityId: Long,
    val roomId: Long,
    val communityName: String,
    val roomName: String,
)

suspend fun prepareFavoriteTopicScenario(
    createAuthenticatedSession: suspend () -> AuthenticatedSession,
): FavoriteTopicScenario {
    val topicContent = "appium-favorite-topic-${System.currentTimeMillis()}"
    val authenticated = createAuthenticatedSession()
    try {
        val topicId = createTopicByApi(
            authenticated.sessionManager,
            ObjectType.USER,
            authenticated.sessionManager.model.uid ?: error("not login"),
            topicContent
        )
        return FavoriteTopicScenario(authenticated, topicId, topicContent)
    } catch (throwable: Throwable) {
        authenticated.sessionManager.client.close()
        throw throwable
    }
}

suspend fun prepareSubscriptionTopicScenario(
    createAuthenticatedSession: suspend () -> AuthenticatedSession,
): SubscriptionTopicScenario {
    val now = System.currentTimeMillis()
    val topicContent = "appium-subscription-topic-$now"
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
        return SubscriptionTopicScenario(viewer, topicId, communityName, topicContent)
    } catch (throwable: Throwable) {
        viewer?.sessionManager?.client?.close()
        throw throwable
    } finally {
        owner.sessionManager.client.close()
    }
}

suspend fun prepareCommunityRoomScenario(
    createAuthenticatedSession: suspend () -> AuthenticatedSession,
): PreparedCommunityRoomScenario {
    val now = System.currentTimeMillis()
    val owner = createAuthenticatedSession()
    var viewer: AuthenticatedSession? = null
    try {
        val aidSuffix = (now % 1_000_000).toString().padStart(6, '0')
        val communityName = "community-$aidSuffix"
        val roomName = "room-$aidSuffix"
        val communityId = createCommunityByApi(owner.sessionManager, communityName, "c$aidSuffix")
        val roomId = createRoomByApi(owner.sessionManager, roomName, "r$aidSuffix", communityId)
        createTopicByApi(owner.sessionManager, ObjectType.COMMUNITY, communityId, "appium-owner-community-topic-$now")
        viewer = createAuthenticatedSession()
        viewer.sessionManager.joinCommunity(communityId).getOrThrow()
        return PreparedCommunityRoomScenario(owner.session, viewer.session, communityId, roomId, communityName, roomName)
    } catch (throwable: Throwable) {
        viewer?.sessionManager?.client?.close()
        throw throwable
    } finally {
        owner.sessionManager.client.close()
        viewer?.sessionManager?.client?.close()
    }
}

suspend fun waitUntilTopicFavorited(
    sessionManager: UserSessionManager,
    topicId: Long,
    timeoutMillis: Long = java.time.Duration.ofSeconds(30).toMillis(),
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (sessionManager.getTopicInfo(topicId).getOrThrow().favoriteId != null) return
        delay(500.milliseconds)
    }
    error("Topic not marked as favorite: $topicId")
}

suspend fun waitUntilTopicSubscribed(
    sessionManager: UserSessionManager,
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

private suspend fun createCommunityByApi(manager: UserSessionManager, name: String, aid: String): Long =
    manager.createCommunity(NewCommunity(name, aid)).getOrThrow().id

private suspend fun createRoomByApi(
    manager: UserSessionManager,
    name: String,
    aid: String,
    communityId: Long,
): Long = manager.createRoom(NewRoom(name, aid, communityId = communityId)).getOrThrow().id

private suspend fun createTopicByApi(
    manager: UserSessionManager,
    parentType: ObjectType,
    parentId: Long,
    content: String,
): Long = manager.createTopic(parentType, parentId, content).getOrThrow().id
