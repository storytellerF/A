/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TestName
import kotlin.time.Duration.Companion.minutes

open class AppiumTestBase {
    @get:Rule
    val name = TestName()
}

fun runAppiumBlockingTest(block: suspend () -> Unit) =
    runBlocking {
    withTimeout(10.minutes) {
        block()
    }
}
