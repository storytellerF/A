package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun SettingOptionView(title: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(shape).clickable {
        onClick()
    }.padding(18.dp)) {
        Text(title)
        Spacer(modifier = Modifier.weight(1f))
        content()
    }
    HorizontalDivider()
}
