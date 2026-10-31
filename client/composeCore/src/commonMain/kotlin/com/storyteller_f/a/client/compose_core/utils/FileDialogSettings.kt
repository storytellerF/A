/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

/** Native dialogs borrow the owning window identity for the duration of the request. */
val LocalFileDialogSettings = staticCompositionLocalOf { FileKitDialogSettings.createDefault() }
