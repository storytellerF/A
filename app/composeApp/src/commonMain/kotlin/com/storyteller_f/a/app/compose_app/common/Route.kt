package com.storyteller_f.a.app.compose_app.common

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
import com.storyteller_f.a.app.compose_app.FileViewData
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.pages.HomePage
import com.storyteller_f.a.app.compose_app.pages.PreferencePage
import com.storyteller_f.a.app.compose_app.pages.community.CommunityComposePage
import com.storyteller_f.a.app.compose_app.pages.community.CommunityPage
import com.storyteller_f.a.app.compose_app.pages.community.CommunitySettingPage
import com.storyteller_f.a.app.compose_app.pages.file.FileExplorerPage
import com.storyteller_f.a.app.compose_app.pages.file.FileViewPage
import com.storyteller_f.a.app.compose_app.pages.room.RoomComposePage
import com.storyteller_f.a.app.compose_app.pages.room.RoomPage
import com.storyteller_f.a.app.compose_app.pages.room.RoomSettingPage
import com.storyteller_f.a.app.compose_app.pages.title.TitleComposePage
import com.storyteller_f.a.app.compose_app.pages.topic.ReactionListPage
import com.storyteller_f.a.app.compose_app.pages.topic.TopicComposePage
import com.storyteller_f.a.app.compose_app.pages.topic.TopicPage
import com.storyteller_f.a.app.compose_app.pages.user.LoginPage
import com.storyteller_f.a.app.compose_app.pages.user.MemberPage
import com.storyteller_f.a.app.compose_app.pages.user.UserFavoritePage
import com.storyteller_f.a.app.compose_app.pages.user.UserPage
import com.storyteller_f.a.app.compose_app.pages.user.UserSettingPage
import com.storyteller_f.a.app.compose_app.pages.user.UserSubscriptionPage
import com.storyteller_f.a.app.compose_app.utils.getDeepLinkHost
import com.storyteller_f.a.app.compose_app.utils.getDeepLinkScheme
import com.storyteller_f.shared.commonJson
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
import kotlinx.serialization.SerialName
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
data class TopicComposeScreen(val json: String)

@Serializable
sealed interface TopicComposeData {
    fun getMediaTarget(): ObjectTuple?
    fun getParent(): ObjectTuple

    @Serializable
    @SerialName("public-room")
    data class PublicRoom(
        val roomId: PrimaryKey,
        val communityId: PrimaryKey,
        val parentTuple: ObjectTuple
    ) : TopicComposeData {
        override fun getMediaTarget(): ObjectTuple? {
            return null
        }

        override fun getParent() = parentTuple
    }

    @Serializable
    @SerialName("private-room")
    data class PrivateRoom(
        val roomId: PrimaryKey,
        val parentTuple: ObjectTuple
    ) : TopicComposeData {
        override fun getMediaTarget(): ObjectTuple {
            return roomId ob ObjectType.ROOM
        }

        override fun getParent() = parentTuple
    }

    @Serializable
    @SerialName("user")
    data class User(
        val uid: PrimaryKey,
        val objectTuple: ObjectTuple
    ) : TopicComposeData {
        override fun getMediaTarget(): ObjectTuple? {
            return null
        }

        override fun getParent() = objectTuple
    }

    @Serializable
    @SerialName("community")
    data class Community(val communityId: PrimaryKey, val objectTuple: ObjectTuple) :
        TopicComposeData {
        override fun getMediaTarget(): ObjectTuple? {
            return null
        }

        override fun getParent(): ObjectTuple {
            return objectTuple
        }
    }
}

@Serializable
data class MemberScreen(val objectType: String, val objectId: PrimaryKey)

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
data object FileExplorerScreen

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

    fun gotoFileExplorer()
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

    override fun gotoTopicCompose(
        data: TopicComposeData,
    ) {
        val text = commonJson.encodeToString(data)
        navigator.navigate(TopicComposeScreen(text))
    }

    override fun gotoMemberPage(
        objectId: PrimaryKey,
        objectType: ObjectType,
    ) {
        navigator.navigate(MemberScreen(objectType.name, objectId))
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

    override fun gotoFileExplorer() {
        navigator.navigate(FileExplorerScreen)
    }
}

@OptIn(ExperimentalResourceApi::class)
fun NavGraphBuilder.buildRootNav(
    navigator: NavHostController,
) {
    buildMainScreen()
    buildComposeScreen(navigator)
    composable<AboutScreen> {
        val libraries by produceLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
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
        CommunityPage(
            screen.communityId,
            screen.showDialog == true
        )
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

    composable<MemberScreen> {
        val (objectType, objectId) = it.toRoute<MemberScreen>()
        MemberPage(objectId, ObjectType.valueOf(objectType))
    }
    composable<UserScreen> {
        val route = it.toRoute<UserScreen>()
        UserPage(route.uid)
    }
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
    composable<FileExplorerScreen> {
        FileExplorerPage()
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
    composable<TopicComposeScreen> {
        val route = it.toRoute<TopicComposeScreen>()
        val composeData = commonJson.decodeFromString<TopicComposeData>(route.json)
        TopicComposePage(composeData) {
            navigator.popBackStack()
        }
    }
}

object ExternalUriHandler {
    // Storage for when a URI arrives before the listener is set up
    private var cached: String? = null

    var listener: ((uri: String) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                // When a listener is set and `cached` is not empty,
                // immediately invoke the listener with the cached URI
                cached?.let { value.invoke(it) }
                cached = null
            }
        }

    // When a new URI arrives, cache it.
    // If the listener is already set, invoke it and clear the cache immediately.
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
