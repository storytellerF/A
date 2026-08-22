/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.components

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
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.client.compose_core.utils.appiumSemantics

data class NavRoute(val path: String, val icon: ImageVector, val label: String)

internal const val HOME_START_DESTINATION_ROOMS = "/rooms"

@Composable
fun CustomRailNav(
    currentEntry: String?,
    navRoutes: List<NavRoute>,
    unreadRoomsBadge: Boolean = false,
    navigate: (String) -> Unit = {},
) {
    NavigationRail(modifier = Modifier.padding(horizontal = 8.dp)) {
        navRoutes.forEach { route ->
            val showBadge = route.path == HOME_START_DESTINATION_ROOMS && unreadRoomsBadge
            val onClick = { navigate(route.path) }
            NavigationRailItem(
                selected = currentEntry == route.path,
                onClick = onClick,
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
                modifier =
                Modifier.appiumSemantics(
                    testTag = route.label,
                    text = route.label,
                    onClick = onClick,
                ),
            )
        }
    }
}

@Composable
fun CustomBottomNav(
    path: String,
    navRoutes: List<NavRoute>,
    unreadRoomsBadge: Boolean = false,
    navigate: (String) -> Unit = { },
) {
    NavigationBar {
        navRoutes.forEach { route ->
            val showBadge = route.path == HOME_START_DESTINATION_ROOMS && unreadRoomsBadge
            val onClick = { navigate(route.path) }
            NavigationBarItem(
                selected = path == route.path,
                onClick = onClick,
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
                modifier =
                Modifier.appiumSemantics(
                    testTag = route.label,
                    text = route.label,
                    onClick = onClick,
                ),
            )
        }
    }
}
