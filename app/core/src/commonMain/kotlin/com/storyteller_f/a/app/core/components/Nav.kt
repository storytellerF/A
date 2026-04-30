package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

class NavRoute(val path: String, val icon: ImageVector, val label: String)

internal const val HOME_START_DESTINATION_ROOMS = "/rooms"

@Composable
fun CustomRailNav(
    currentEntry: String?,
    navRoutes: List<NavRoute>,
    unreadRoomsBadge: Boolean = false,
    navigate: (String) -> Unit = {}
) {
    NavigationRail(modifier = Modifier.padding(horizontal = 8.dp)) {
        navRoutes.forEach { route ->
            val showBadge = route.path == HOME_START_DESTINATION_ROOMS && unreadRoomsBadge
            NavigationRailItem(
                selected = currentEntry == route.path,
                onClick = { navigate(route.path) },
                icon = {
                    if (showBadge) {
                        BadgedBox(badge = { Badge { } }) {
                            Icon(imageVector = route.icon, contentDescription = route.label)
                        }
                    } else {
                        Icon(imageVector = route.icon, contentDescription = route.label)
                    }
                },
                label = { Text(route.label) }
            )
        }
    }
}

@Composable
fun CustomBottomNav(
    path: String,
    navRoutes: List<NavRoute>,
    unreadRoomsBadge: Boolean = false,
    navigate: (String) -> Unit = { }
) {
    NavigationBar {
        navRoutes.forEach { route ->
            val showBadge = route.path == HOME_START_DESTINATION_ROOMS && unreadRoomsBadge
            NavigationBarItem(
                selected = path == route.path,
                onClick = { navigate(route.path) },
                icon = {
                    if (showBadge) {
                        BadgedBox(badge = { Badge { } }) {
                            Icon(imageVector = route.icon, contentDescription = route.label)
                        }
                    } else {
                        Icon(imageVector = route.icon, contentDescription = route.label)
                    }
                },
                label = { Text(route.label) },
                modifier = Modifier.testTag(route.label)
            )
        }
    }
}
