/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.panel

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
