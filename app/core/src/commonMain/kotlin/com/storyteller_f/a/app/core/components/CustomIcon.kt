package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.tclement.fonticons.FontIcon
import dev.tclement.fonticons.ProvideIconParameters

sealed interface IconRes {
    data class Vector(val vector: ImageVector, val description: String = "") : IconRes
    data class Font(val char: Char, val description: String = "") : IconRes
    data object Loading : IconRes
}

fun Modifier.clickableIfNotNull(onClick: (() -> Unit)?): Modifier {
    if (onClick != null) {
        return clickable {
            onClick.invoke()
        }
    }
    return this
}

@Composable
fun CustomIcon(icon: IconRes, onClick: (() -> Unit)? = null) {
    when (icon) {
        is IconRes.Font -> {
            ProvideIconParameters(
                size = 20.dp,
                tintProvider = LocalContentColor
            ) {
                FontIcon(
                    icon.char,
                    icon.description,
                    modifier = Modifier.clickableIfNotNull(onClick).size(20.dp)
                )
            }
        }

        is IconRes.Vector -> {
            Icon(
                imageVector = icon.vector,
                contentDescription = icon.description,
                modifier = Modifier.clickableIfNotNull(onClick).size(20.dp)
            )
        }

        IconRes.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
}

@Composable
fun CustomIcon(vector: ImageVector, onClick: (() -> Unit)? = null) {
    CustomIcon(IconRes.Vector(vector), onClick)
}
