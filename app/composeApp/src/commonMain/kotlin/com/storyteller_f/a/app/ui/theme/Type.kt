/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import com.storyteller_f.a.client.compose_core.utils.rememberPlatformTypography

private val defaultTypography = Typography()

@Composable
internal fun rememberAppTypography(): Typography = rememberPlatformTypography(defaultTypography)
