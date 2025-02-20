package com.storyteller_f.a.app.utils

class Platform(val hasNativeBack: Boolean, val isActive: Boolean = true)

expect val platform: Platform
