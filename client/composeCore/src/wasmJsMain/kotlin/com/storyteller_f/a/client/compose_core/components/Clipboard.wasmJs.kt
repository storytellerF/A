/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.components

import androidx.compose.ui.platform.Clipboard

actual suspend fun Clipboard.setText(string: String) = Unit
