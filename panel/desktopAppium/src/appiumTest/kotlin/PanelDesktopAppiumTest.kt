/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import kotlin.test.Test

class PanelDesktopAppiumTest : AppiumTestBase() {
    private val targetHelper = PanelAppiumHelper()
    private val platformHelper = DesktopAppiumHelper()

    @Test
    fun `test panel sign in by injected private session`() =
        testPanelInjectedSessionByHelper(name.methodName, targetHelper, platformHelper)
}
