/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.test.resolveTestPresetPath
import java.io.File

data class AppiumPorts(val server: Int, val ws: Int, val expireUserSessions: (() -> Unit)? = null)

data class AuthenticatedSession(val session: InjectedSession, val sessionManager: UserSessionManager)

data class AppUnderTest(val packageName: String, val mainActivityClassName: String)

fun resolveAppiumPresetPath(): File = resolveTestPresetPath()
