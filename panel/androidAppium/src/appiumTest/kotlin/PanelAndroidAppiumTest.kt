/*
 * This is a private project. All rights reserved.
 */

import kotlin.test.Test

class PanelAndroidAppiumTest : AppiumTestBase() {
    private val targetHelper = PanelAppiumHelper()
    private val platformHelper = AndroidAppiumHelper()

    @Test
    fun `test panel sign in by injected private session`() =
        testPanelInjectedSessionByHelper(name.methodName, targetHelper, platformHelper)
}
