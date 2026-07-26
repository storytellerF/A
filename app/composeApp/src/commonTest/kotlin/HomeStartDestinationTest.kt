import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.storyteller_f.a.app.AppListDetailDestination
import com.storyteller_f.a.app.AppListDetailPane
import com.storyteller_f.a.app.AppListDetailScene
import com.storyteller_f.a.app.addAppDetail
import com.storyteller_f.a.app.appListDetailDestination
import com.storyteller_f.a.app.calculateAppPaneDirective
import com.storyteller_f.a.app.common.AboutScreen
import com.storyteller_f.a.app.common.CommunityScreen
import com.storyteller_f.a.app.common.HomeScreen
import com.storyteller_f.a.app.common.RoomScreen
import com.storyteller_f.a.app.common.TopicScreen
import com.storyteller_f.a.app.common.UserScreen
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_COMMUNITIES
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_ROOMS
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_WORLD
import com.storyteller_f.a.app.pages.HomeRoute
import com.storyteller_f.a.app.pages.homePageFromPreference
import com.storyteller_f.a.app.pages.homeRouteFromPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeStartDestinationTest {
    @Test
    fun shouldMapPreferenceToExpectedHomeDestination() {
        assertEquals(HomeRoute.World, homeRouteFromPreference(HOME_START_DESTINATION_WORLD))
        assertEquals(HomeRoute.Communities, homeRouteFromPreference(HOME_START_DESTINATION_COMMUNITIES))
        assertEquals(HomeRoute.Rooms, homeRouteFromPreference(HOME_START_DESTINATION_ROOMS))
        assertEquals(HomeRoute.World, homeRouteFromPreference("unknown"))

        assertEquals(0, homePageFromPreference(HOME_START_DESTINATION_WORLD))
        assertEquals(1, homePageFromPreference(HOME_START_DESTINATION_COMMUNITIES))
        assertEquals(2, homePageFromPreference(HOME_START_DESTINATION_ROOMS))
        assertEquals(0, homePageFromPreference("unknown"))
    }

    @Test
    fun homeAndContentRoutesUseMatchingListDetailScene() {
        assertEquals(
            AppListDetailDestination(AppListDetailScene.Home, AppListDetailPane.List),
            HomeScreen.appListDetailDestination(),
        )
        listOf(
            CommunityScreen(1),
            RoomScreen(1),
            TopicScreen(1),
            UserScreen(1),
        ).forEach { detail ->
            assertEquals(
                AppListDetailDestination(AppListDetailScene.Home, AppListDetailPane.Detail),
                detail.appListDetailDestination(),
            )
        }
        assertNull(AboutScreen.appListDetailDestination())
    }

    @Test
    fun selectingAnotherHomeDetailReplacesCurrentDetail() {
        val backStack = NavBackStack<NavKey>(HomeScreen, RoomScreen(1))

        backStack.addAppDetail(TopicScreen(2))

        assertEquals(
            listOf(HomeScreen, TopicScreen(2)),
            backStack,
        )
    }

    @Test
    fun detailOutsideImmediateHomePairPreservesHistory() {
        val backStack = NavBackStack<NavKey>(HomeScreen, AboutScreen)

        backStack.addAppDetail(UserScreen(2))

        assertEquals(
            listOf(HomeScreen, AboutScreen, UserScreen(2)),
            backStack,
        )
    }

    @Test
    fun appListDetailUsesContentWidth() {
        val compactDirective =
            calculateAppPaneDirective(
                contentSize = DpSize(600.dp, 900.dp),
                windowPosture = Posture(),
            )
        val expandedDirective =
            calculateAppPaneDirective(
                contentSize = DpSize(840.dp, 900.dp),
                windowPosture = Posture(),
            )

        assertEquals(1, compactDirective.maxHorizontalPartitions)
        assertEquals(2, expandedDirective.maxHorizontalPartitions)
    }
}
