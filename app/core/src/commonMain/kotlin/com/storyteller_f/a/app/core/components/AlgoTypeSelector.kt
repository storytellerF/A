package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.storyteller_f.shared.model.AlgoType

@Composable
fun AlgoTypeSelector(
    selected: AlgoType,
    onSelected: (AlgoType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AlgoType.entries.forEach {
            val isSelected = it == selected
            Button(
                onClick = { onSelected(it) },
                colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
            ) {
                Text(it.name)
            }
        }
    }
}
