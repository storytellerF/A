package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.IconRes

@Composable
fun ButtonNav(icon: ImageVector, title: String, onClick: () -> Unit = {}) {
    ButtonNav(IconRes.Vector(icon), title, onClick = onClick)
}

@Composable
fun ButtonNav(icon: Char, title: String, onClick: () -> Unit = {}) {
    ButtonNav(IconRes.Font(icon), title, onClick = onClick)
}

@Composable
fun ButtonNav(
    icon: IconRes,
    title: String,
    suffix: @Composable () -> Unit = {},
    onClick: () -> Unit = {}
) {
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
        Spacer(modifier = Modifier.weight(1f))
        suffix()
    }
}
