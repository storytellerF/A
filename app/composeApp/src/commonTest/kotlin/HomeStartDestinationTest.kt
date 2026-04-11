import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_COMMUNITIES
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_ROOMS
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_WORLD
import com.storyteller_f.a.app.pages.HomeRoute
import com.storyteller_f.a.app.pages.homePageFromPreference
import com.storyteller_f.a.app.pages.homeRouteFromPreference
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
