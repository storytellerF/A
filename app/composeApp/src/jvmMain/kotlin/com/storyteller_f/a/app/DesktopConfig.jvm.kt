package com.storyteller_f.a.app

fun getDesktopServerUrl(): String = System.getProperty("appium.server.url") ?: AppConfig.SERVER_URL

fun getDesktopWsServerUrl(): String = System.getProperty("appium.ws.url") ?: AppConfig.WS_SERVER_URL
