/*
 * This is a private project. All rights reserved.
*/

package com.storyteller_f.a.client.asciidoc_parser

import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
actual open class PlatformHeadlessTest {
    @Before
    fun setup() {
    }
}
