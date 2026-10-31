/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

fun AppiumTestBase<AppAppiumHelper, AndroidAppiumHelper>.testAndroidPip(paused: Boolean) =
    withUploadedFile(PreviewFile.VIDEO, topic = false) { scope, data ->
        val driver = scope.driver as AndroidAppTestDriver
        openUploadedPreview(driver, data)
        driver.withAnimatedMediaControls {
            driver.clickByDescription("play")
            driver.assertVideoPlaying(data.file.name)
            if (paused) driver.pauseVideo(data.file.name)
            val snapshots =
                if (paused) {
                    listOf("system-picture-in-picture")
                } else {
                    listOf("system-picture-in-picture", "picture-in-picture-reentered")
                }
            for (snapshot in snapshots) {
                driver.pressHomeAndAssertPip(targetHelper.androidApp.packageName, expected = !paused)
                if (!paused) driver.assertVideoFrame(targetHelper.androidApp.packageName, data.file.name)
                driver.saveSnapshot(snapshot)
                driver.restoreFromPip(targetHelper.androidApp.packageName)
                driver.assertVisibleByText(data.file.name)
                if (!paused) driver.assertVideoPlaying(data.file.name)
            }
        }
    }

fun AppiumTestBase<AppAppiumHelper, AndroidAppiumHelper>.testAndroidInlineVideo() =
    withUploadedFile(PreviewFile.VIDEO, topic = true) { scope, data ->
        val driver = scope.driver as AndroidAppTestDriver
        openUploadedTopic(driver, data)
        driver.assertVisibleByText("Preview: ${data.file.name}")
        driver.withAnimatedMediaControls {
            driver.clickByDescription("play")
            driver.assertVideoPlaying(data.file.name)
            driver.assertVisibleByText(requireNotNull(data.topicMarker))
            driver.saveSnapshot("topic-inline-video-playing")
        }
    }
