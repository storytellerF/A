package com.storyteller_f.a.panel

fun getDesktopPanelServerUrl(): String = System.getProperty("appium.server.url") ?: PanelConfig.SERVER_URL
