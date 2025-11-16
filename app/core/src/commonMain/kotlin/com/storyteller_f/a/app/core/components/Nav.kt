package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.padding
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

@Composable
fun CustomRailNav(
    currentEntry: String?,
    navRoutes: List<NavRoute>,
    navigate: (String) -> Unit = {}
) {
    NavigationRail(modifier = Modifier.padding(horizontal = 8.dp)) {
        navRoutes.forEach {
            NavigationRailItem(currentEntry == it.path, {
                navigate(it.path)
            }, icon = {
                Icon(imageVector = it.icon, contentDescription = it.label)
            }, label = {
                Text(it.label)
            })
        }
    }
}

@Composable
fun CustomBottomNav(
    path: String,
    navRoutes: List<NavRoute>,
    navigate: (String) -> Unit = { }
) {
    NavigationBar {
        navRoutes.forEach {
            NavigationBarItem(path == it.path, {
                navigate(it.path)
            }, {
                Icon(imageVector = it.icon, it.label)
            }, label = {
                Text(it.label)
            }, modifier = Modifier.testTag(it.label))
        }
    }
}