package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp


@Composable
fun ButtonNav(icon: ImageVector, title: String, onClick: () -> Unit = {}) {
    ButtonNav(IconRes.Vector(icon), title, onClick)
}

@Composable
fun ButtonNav(icon: Char, title: String, onClick: () -> Unit = {}) {
    ButtonNav(IconRes.Font(icon), title, onClick)
}

@Composable
fun ButtonNav(icon: IconRes, title: String, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(shape).clickable {
            onClick()
        }.padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        CustomIcon(icon)
        Text(title)
    }
}
