package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.rememberAlertDialogController

@Composable
fun SimpleMessageWithButton(string: String, key: String) {
    val alterDialogController = rememberAlertDialogController()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(string, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton({
            alterDialogController.showMessage(key, string)
        }) {
            Icon(Icons.Default.Fullscreen, "fullscreen")
        }
    }
    CustomAlertDialog(alterDialogController, {
        alterDialogController.close()
    }) {
    }
}

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
