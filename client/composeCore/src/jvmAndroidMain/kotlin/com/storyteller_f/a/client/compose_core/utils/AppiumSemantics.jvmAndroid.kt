/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import androidx.compose.ui.Modifier

/** Native no-op implementation of [appiumSemantics]. */
@Suppress("LibraryEntitiesShouldNotBePublic", "LongParameterList")
actual fun Modifier.appiumSemantics(
    testTag: String?,
    description: String?,
    text: String?,
    input: Boolean,
    inputValue: String?,
    onInputValueChange: ((String) -> Unit)?,
    onClick: (() -> Unit)?,
): Modifier = this
