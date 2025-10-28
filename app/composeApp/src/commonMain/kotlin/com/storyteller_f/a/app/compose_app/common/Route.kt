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
import androidx.navigation.toRoute
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.storyteller_f.a.app.compose_app.FileViewInfo
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.pages.HomePage
import com.storyteller_f.a.app.compose_app.pages.PreferencePage
import com.storyteller_f.a.app.compose_app.pages.community.CommunityComposePage
import com.storyteller_f.a.app.compose_app.pages.community.CommunityPage
import com.storyteller_f.a.app.compose_app.pages.community.CommunitySettingPage
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
data class CommunityScreen(val communityId: PrimaryKey, val showDialog: Boolean)

@Serializable
data class RoomScreen(val roomId: PrimaryKey, val showDialog: Boolean)

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
data class MediaScreen(val json: String)

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
        val route = MediaScreen(commonJson.encodeToString<FileViewInfo>(FileViewInfo.Regular(info)))
        navigator.navigate(route)
    }

    override fun gotoLocalImage(url: String) {
        val route =
            MediaScreen(commonJson.encodeToString<FileViewInfo>(FileViewInfo.LocalImage(url)))
        navigator.navigate(route)
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
    composable<MediaScreen> {
        val route = it.toRoute<MediaScreen>()
        val pack = commonJson.decodeFromString<FileViewInfo>(route.json)
        FileViewPage(pack)
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
            screen.showDialog
        )
    }
    composable<RoomScreen> {
        val screen = it.toRoute<RoomScreen>()
        RoomPage(screen.roomId, screen.showDialog)
    }
    composable<TopicScreen> {
        TopicPage(it.toRoute<TopicScreen>().topicId)
    }

    composable<MemberScreen> {
        val (objectType, objectId) = it.toRoute<MemberScreen>()
        MemberPage(
            objectId,
            ObjectType.valueOf(objectType)
        )
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
