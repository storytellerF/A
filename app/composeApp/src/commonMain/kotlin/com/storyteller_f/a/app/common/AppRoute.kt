package com.storyteller_f.a.app.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.core.components.FileViewData
import com.storyteller_f.a.app.pages.HomePage
import com.storyteller_f.a.app.pages.PreferencePage
import com.storyteller_f.a.app.pages.community.CommunityComposePage
import com.storyteller_f.a.app.pages.community.CommunityPage
import com.storyteller_f.a.app.pages.community.CommunitySettingPage
import com.storyteller_f.a.app.pages.file.FileExplorerPage
import com.storyteller_f.a.app.pages.file.FileRefsPage
import com.storyteller_f.a.app.pages.file.FileViewPage
import com.storyteller_f.a.app.pages.room.RoomComposePage
import com.storyteller_f.a.app.pages.room.RoomPage
import com.storyteller_f.a.app.pages.room.RoomSettingPage
import com.storyteller_f.a.app.pages.title.TitleComposePage
import com.storyteller_f.a.app.pages.topic.ReactionListPage
import com.storyteller_f.a.app.pages.topic.TopicComposeData
import com.storyteller_f.a.app.pages.topic.TopicComposePage
import com.storyteller_f.a.app.pages.topic.TopicPage
import com.storyteller_f.a.app.pages.user.LoginPage
import com.storyteller_f.a.app.pages.user.MemberPage
import com.storyteller_f.a.app.pages.user.UserCommentsPage
import com.storyteller_f.a.app.pages.user.UserFavoritePage
import com.storyteller_f.a.app.pages.user.UserPage
import com.storyteller_f.a.app.pages.user.UserReactionRecordsPage
import com.storyteller_f.a.app.pages.user.UserSettingPage
import com.storyteller_f.a.app.pages.user.UserSubscriptionPage
import com.storyteller_f.a.app.utils.getDeepLinkHost
import com.storyteller_f.a.app.utils.getDeepLinkScheme
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.reflect.KClass

@Serializable
data object HomeScreen

@Serializable
data class CommunityScreen(val communityId: PrimaryKey, val showDialog: Boolean? = null)

@Serializable
data class RoomScreen(val roomId: PrimaryKey, val showDialog: Boolean? = null)

@Serializable
data object SignSessionScreen

@Serializable
data class TopicScreen(val topicId: PrimaryKey)

@Serializable
data object AboutScreen

@Serializable
data class UserScreen(val uid: PrimaryKey)

@Serializable
data class TopicComposePublicRoomScreen(
    val roomId: PrimaryKey,
    val communityId: PrimaryKey,
)

@Serializable
data class TopicComposePublicRoomTopicScreen(
    val roomId: PrimaryKey,
    val communityId: PrimaryKey,
    val topicId: PrimaryKey
)

@Serializable
data class TopicComposePrivateRoomScreen(
    val roomId: PrimaryKey,
)

@Serializable
data class TopicComposePrivateRoomTopicScreen(
    val roomId: PrimaryKey,
    val topicId: PrimaryKey
)

@Serializable
data class TopicComposeUserScreen(
    val uid: PrimaryKey,
)

@Serializable
data class TopicComposeUserTopicScreen(
    val uid: PrimaryKey,
    val topicId: PrimaryKey
)

@Serializable
data class TopicComposeCommunityScreen(val communityId: PrimaryKey)

@Serializable
data class TopicComposeCommunityTopicScreen(val communityId: PrimaryKey, val topicId: PrimaryKey)

@Serializable
data class RoomMemberScreen(val objectId: PrimaryKey)

@Serializable
data class CommunityMemberScreen(val objectId: PrimaryKey)

@Serializable
data object UserSettingScreen

@Serializable
data object PreferenceScreen

@Serializable
data class LocalImageScreen(val url: String)

@Serializable
data class FileInfoScreen(val fileId: PrimaryKey)

@Serializable
data object TitleComposeScreen

@Serializable
data object CommunityComposeScreen

@Serializable
data object RoomComposeScreen

@Serializable
data class CommunitySettingScreen(val communityId: PrimaryKey)

@Serializable
data class RoomSettingScreen(val roomId: PrimaryKey)

@Serializable
data class ReactionListScreen(val topicId: PrimaryKey)

@Serializable
data object FavoriteScreen

@Serializable
data object SubscriptionScreen

@Serializable
data object UserReactionRecordsScreen

@Serializable
data object UserCommentsScreen

@Serializable
data object FileExplorerScreen

@Serializable
data class RoomFileExplorerScreen(val roomId: PrimaryKey)

@Serializable
data class CommunityFileExplorerScreen(val communityId: PrimaryKey)

@Serializable
data class FileRefsScreen(val fileId: PrimaryKey)

inline fun <reified T : Any> AppNav.toRoute(): T? {
    if (!hasRoute(T::class)) return null
    return currentDestination?.toRoute<T>()
}

@Composable
inline fun <reified T : Any> AppNavFactory.hasRouteFlow(crossinline block: (T) -> Boolean = { true }): State<Boolean> {
    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode) {
        return remember {
            mutableStateOf(false)
        }
    }
    return newAppNav().currentDestinationFlow.filterNotNull().map {
        it.destination.hasRoute<T>() && block(it.toRoute<T>())
    }.collectAsState(false)
}

interface AppNav {
    val currentDestination: NavBackStackEntry?

    val currentDestinationFlow: StateFlow<NavBackStackEntry?>

    fun <T : Any> hasRoute(any: KClass<T>): Boolean {
        return currentDestination?.destination?.hasRoute(any) == true
    }

    fun gotoLogin()

    fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean)

    fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean)

    fun gotoTopic(topicId: PrimaryKey)

    fun gotoHome()

    fun gotoTopicCompose(data: TopicComposeData)

    fun gotoMemberPage(objectId: PrimaryKey, objectType: ObjectType)

    fun gotoAbout()

    fun gotoUser(uid: PrimaryKey)

    fun back()

    fun gotoUserSetting()

    fun gotoPreference()

    fun gotoMedia(info: FileInfo)

    fun gotoLocalImage(url: String)

    fun gotoTitleCompose()

    fun gotoCommunityCompose()

    fun gotoRoomCompose()

    fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType)

    fun gotoReactionListPage(topicId: PrimaryKey)

    fun gotoFavoritePage()

    fun gotoSubscriptionPage()

    fun gotoUserReactionRecordsPage()

    fun gotoUserCommentsPage()

    fun gotoFileExplorer(objectTuple: ObjectTuple? = null)

    fun gotoFileRefs(fileId: PrimaryKey)
}

interface AppNavFactory {
    fun newAppNav(): AppNav

    companion object {
        val EMPTY = object : AppNavFactory {
            override fun newAppNav(): AppNav {
                error("no app nav")
            }
        }
    }
}

fun newAppNav(navigator: NavHostController, scope: CoroutineScope) = object : AppNav {
    override val currentDestination: NavBackStackEntry?
        get() = navigator.currentBackStackEntry
    override val currentDestinationFlow: StateFlow<NavBackStackEntry?>
        get() = navigator.currentBackStackEntryFlow.stateIn(scope, SharingStarted.Eagerly, null)

    override fun gotoLogin() {
        navigator.navigate(route = SignSessionScreen)
    }

    override fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean) {
        navigator.navigate(route = RoomScreen(roomId, showDialog))
    }

    override fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean) {
        navigator.navigate(route = CommunityScreen(communityId, showDialog))
    }

    override fun gotoTopic(topicId: PrimaryKey) {
        navigator.navigate(route = TopicScreen(topicId))
    }

    override fun gotoHome() {
        navigator.popBackStack(HomeScreen, false)
    }

    override fun gotoTopicCompose(data: TopicComposeData) {
        when (data) {
            is TopicComposeData.PublicRoom -> {
                if (data.parentTuple.objectType == ObjectType.TOPIC) {
                    navigator.navigate(
                        TopicComposePublicRoomTopicScreen(data.roomId, data.communityId, data.parentTuple.objectId)
                    )
                } else {
                    navigator.navigate(TopicComposePublicRoomScreen(data.roomId, data.communityId))
                }
            }

            is TopicComposeData.PrivateRoom -> {
                if (data.parentTuple.objectType == ObjectType.TOPIC) {
                    navigator.navigate(TopicComposePrivateRoomTopicScreen(data.roomId, data.parentTuple.objectId))
                } else {
                    navigator.navigate(TopicComposePrivateRoomScreen(data.roomId))
                }
            }

            is TopicComposeData.User -> {
                if (data.objectTuple.objectType == ObjectType.TOPIC) {
                    navigator.navigate(TopicComposeUserTopicScreen(data.uid, data.objectTuple.objectId))
                } else {
                    navigator.navigate(TopicComposeUserScreen(data.uid))
                }
            }

            is TopicComposeData.Community -> {
                if (data.objectTuple.objectType == ObjectType.TOPIC) {
                    navigator.navigate(TopicComposeCommunityTopicScreen(data.communityId, data.objectTuple.objectId))
                } else {
                    navigator.navigate(TopicComposeCommunityScreen(data.communityId))
                }
            }
        }
    }

    override fun gotoMemberPage(
        objectId: PrimaryKey,
        objectType: ObjectType,
    ) {
        if (objectType == ObjectType.COMMUNITY) {
            navigator.navigate(CommunityMemberScreen(objectId))
        } else {
            navigator.navigate(RoomMemberScreen(objectId))
        }
    }

    override fun gotoAbout() {
        navigator.navigate(AboutScreen)
    }

    override fun gotoUser(uid: PrimaryKey) {
        navigator.navigate(UserScreen(uid))
    }

    override fun back() {
        navigator.popBackStack()
    }

    override fun gotoUserSetting() {
        navigator.navigate(UserSettingScreen)
    }

    override fun gotoPreference() {
        navigator.navigate(PreferenceScreen)
    }

    override fun gotoMedia(info: FileInfo) {
        navigator.navigate(FileInfoScreen(info.id))
    }

    override fun gotoLocalImage(url: String) {
        navigator.navigate(LocalImageScreen(url))
    }

    override fun gotoTitleCompose() {
        navigator.navigate(TitleComposeScreen)
    }

    override fun gotoCommunityCompose() {
        navigator.navigate(CommunityComposeScreen)
    }

    override fun gotoRoomCompose() {
        navigator.navigate(RoomComposeScreen)
    }

    override fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType) {
        if (objectType == ObjectType.COMMUNITY) {
            navigator.navigate(CommunitySettingScreen(objectId))
        } else {
            navigator.navigate(RoomSettingScreen(objectId))
        }
    }

    override fun gotoReactionListPage(topicId: PrimaryKey) {
        navigator.navigate(ReactionListScreen(topicId))
    }

    override fun gotoFavoritePage() {
        navigator.navigate(FavoriteScreen)
    }

    override fun gotoSubscriptionPage() {
        navigator.navigate(SubscriptionScreen)
    }

    override fun gotoUserReactionRecordsPage() {
        navigator.navigate(UserReactionRecordsScreen)
    }

    override fun gotoUserCommentsPage() {
        navigator.navigate(UserCommentsScreen)
    }

    override fun gotoFileExplorer(objectTuple: ObjectTuple?) {
        if (objectTuple == null) {
            navigator.navigate(FileExplorerScreen)
        } else {
            when (objectTuple.objectType) {
                ObjectType.ROOM -> navigator.navigate(RoomFileExplorerScreen(objectTuple.objectId))
                ObjectType.COMMUNITY -> navigator.navigate(CommunityFileExplorerScreen(objectTuple.objectId))
                else -> error("Unsupported object type for file explorer: ${objectTuple.objectType}")
            }
        }
    }

    override fun gotoFileRefs(fileId: PrimaryKey) {
        navigator.navigate(FileRefsScreen(fileId))
    }
}

@OptIn(ExperimentalResourceApi::class)
fun NavGraphBuilder.buildRootNav(
    navigator: NavHostController,
) {
    buildMainScreen()
    buildSettingsScreen()
    buildComposeScreen(navigator)
    buildFileExplorerScreen()
    composable<AboutScreen> {
        val libraries by produceLibraries {
            io.github.windedge.table.res.Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        Surface {
            LibrariesContainer(
                libraries,
                Modifier.fillMaxSize().statusBarsPadding(),
                colors = LibraryDefaults.libraryColors()
            )
        }
    }
    composable<FileInfoScreen> {
        val route = it.toRoute<FileInfoScreen>()
        FileViewPage(FileViewData.Regular(route.fileId))
    }

    composable<LocalImageScreen> {
        val route = it.toRoute<LocalImageScreen>()
        FileViewPage(FileViewData.LocalImage(route.url))
    }
    composable<FileRefsScreen> {
        val route = it.toRoute<FileRefsScreen>()
        FileRefsPage(route.fileId)
    }
}

private fun NavGraphBuilder.buildMainScreen() {
    composable<HomeScreen> {
        HomePage()
    }
    composable<SignSessionScreen> {
        LoginPage()
    }
    composable<CommunityScreen> {
        val screen = it.toRoute<CommunityScreen>()
        CommunityPage(screen.communityId, screen.showDialog == true)
    }
    composable<RoomScreen>(
        deepLinks = listOf(navDeepLink<RoomScreen>(basePath = "${getDeepLinkScheme()}://${getDeepLinkHost()}/room"))
    ) {
        val screen = it.toRoute<RoomScreen>()
        RoomPage(screen.roomId, screen.showDialog == true)
    }
    composable<TopicScreen> {
        TopicPage(it.toRoute<TopicScreen>().topicId)
    }

    composable<RoomMemberScreen> {
        val route = it.toRoute<RoomMemberScreen>()
        MemberPage(route.objectId, ObjectType.ROOM)
    }
    composable<CommunityMemberScreen> {
        val route = it.toRoute<CommunityMemberScreen>()
        MemberPage(route.objectId, ObjectType.COMMUNITY)
    }
    composable<UserScreen> {
        val route = it.toRoute<UserScreen>()
        UserPage(route.uid)
    }
    composable<ReactionListScreen> {
        val topicId = it.toRoute<ReactionListScreen>().topicId
        ReactionListPage(topicId)
    }
    composable<FavoriteScreen> {
        UserFavoritePage()
    }
    composable<SubscriptionScreen> {
        UserSubscriptionPage()
    }
    composable<UserReactionRecordsScreen> {
        UserReactionRecordsPage()
    }
    composable<UserCommentsScreen> {
        UserCommentsPage()
    }
}

private fun NavGraphBuilder.buildFileExplorerScreen() {
    composable<FileExplorerScreen> {
        val id = LocalUserInfo.current?.id ?: return@composable
        FileExplorerPage(mediaTarget = id ob ObjectType.USER)
    }
    composable<RoomFileExplorerScreen> {
        val route = it.toRoute<RoomFileExplorerScreen>()
        FileExplorerPage(mediaTarget = route.roomId ob ObjectType.ROOM)
    }
    composable<CommunityFileExplorerScreen> {
        val route = it.toRoute<CommunityFileExplorerScreen>()
        FileExplorerPage(mediaTarget = route.communityId ob ObjectType.COMMUNITY)
    }
}

private fun NavGraphBuilder.buildSettingsScreen() {
    composable<UserSettingScreen> {
        UserSettingPage()
    }
    composable<PreferenceScreen> {
        PreferencePage()
    }
    composable<CommunitySettingScreen> {
        val communityId = it.toRoute<CommunitySettingScreen>().communityId
        CommunitySettingPage(communityId)
    }
    composable<RoomSettingScreen> {
        val roomId = it.toRoute<RoomSettingScreen>().roomId
        RoomSettingPage(roomId)
    }
}

private fun NavGraphBuilder.buildComposeScreen(navigator: NavHostController) {
    composable<TitleComposeScreen> {
        TitleComposePage()
    }
    composable<CommunityComposeScreen> {
        CommunityComposePage()
    }
    composable<RoomComposeScreen> {
        RoomComposePage()
    }
    val backPrePage: () -> Unit = {
        navigator.popBackStack()
    }
    buildTopicComposeRoomScreen(backPrePage)
    buildTopicComposeUserScreen(backPrePage)
    buildTopicComposeCommunityScreen(backPrePage)
}

private fun NavGraphBuilder.buildTopicComposeRoomScreen(backPrePage: () -> Unit) {
    composable<TopicComposePublicRoomScreen> {
        val route = it.toRoute<TopicComposePublicRoomScreen>()
        val composeData = TopicComposeData.PublicRoom(route.roomId, route.communityId, route.roomId ob ObjectType.ROOM)
        TopicComposePage(composeData, backPrePage)
    }
    composable<TopicComposePublicRoomTopicScreen> {
        val route = it.toRoute<TopicComposePublicRoomTopicScreen>()
        val composeData = TopicComposeData.PublicRoom(
            route.roomId,
            route.communityId,
            route.topicId ob ObjectType.TOPIC
        )
        TopicComposePage(composeData, backPrePage)
    }
    composable<TopicComposePrivateRoomScreen> {
        val route = it.toRoute<TopicComposePrivateRoomScreen>()
        val composeData = TopicComposeData.PrivateRoom(route.roomId, route.roomId ob ObjectType.ROOM)
        TopicComposePage(composeData, backPrePage)
    }
    composable<TopicComposePrivateRoomTopicScreen> {
        val route = it.toRoute<TopicComposePrivateRoomTopicScreen>()
        val composeData = TopicComposeData.PrivateRoom(route.roomId, route.topicId ob ObjectType.TOPIC)
        TopicComposePage(composeData, backPrePage)
    }
}

private fun NavGraphBuilder.buildTopicComposeUserScreen(backPrePage: () -> Unit) {
    composable<TopicComposeUserScreen> {
        val route = it.toRoute<TopicComposeUserScreen>()
        val composeData = TopicComposeData.User(route.uid, route.uid ob ObjectType.USER)
        TopicComposePage(composeData, backPrePage)
    }
    composable<TopicComposeUserTopicScreen> {
        val route = it.toRoute<TopicComposeUserTopicScreen>()
        val composeData = TopicComposeData.User(route.uid, route.topicId ob ObjectType.TOPIC)
        TopicComposePage(composeData, backPrePage)
    }
}

private fun NavGraphBuilder.buildTopicComposeCommunityScreen(backPrePage: () -> Unit) {
    composable<TopicComposeCommunityScreen> {
        val route = it.toRoute<TopicComposeCommunityScreen>()
        val composeData = TopicComposeData.Community(route.communityId, route.communityId ob ObjectType.COMMUNITY)
        TopicComposePage(composeData, backPrePage)
    }
    composable<TopicComposeCommunityTopicScreen> {
        val route = it.toRoute<TopicComposeCommunityTopicScreen>()
        val composeData = TopicComposeData.Community(route.communityId, route.topicId ob ObjectType.TOPIC)
        TopicComposePage(composeData, backPrePage)
    }
}

object ExternalUriHandler {
    private var cached: String? = null

    var listener: ((uri: String) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                cached?.let { value.invoke(it) }
                cached = null
            }
        }

    fun onNewUri(uri: String) {
        cached = uri
        listener?.let {
            it.invoke(uri)
            cached = null
        }
    }
}

fun getDeepLink(path: String): String {
    return "${getDeepLinkScheme()}://${getDeepLinkHost()}$path"
}
