/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
actual abstract class PlatformHeadlessTest
