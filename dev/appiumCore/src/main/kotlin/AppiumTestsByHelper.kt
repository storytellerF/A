fun testSignUpByHelper(testName: String, target: AppAppiumHelper, platform: PlatformAppiumHelper) =
    runAppiumBlockingTest {
        platform.runTest(
            testName = testName,
            target = target,
            setup = { AppiumTestSetup(Unit) },
        ) { scope, _ ->
            scenarioSignUp(scope.driver)
        }
    }

fun testSignInAsSystemUserByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { AppiumTestSetup(Unit) },
    ) { scope, _ ->
        scenarioSignInAsSystemUser(scope.driver, target.readSystemPrivateKey())
    }
}

fun testInjectedSessionByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            AppiumTestSetup(
                data = Unit,
                injectedSession = target.createPreRegisteredSession(ports),
            )
        },
    ) { scope, _ ->
        scenarioVerifyInjectedSessionLoaded(scope.driver)
    }
}

fun testPublishTopicInUserSpaceByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            val session = target.createPreRegisteredSession(ports)
            AppiumTestSetup(data = Unit, injectedSession = session)
        },
    ) { scope, _ ->
        scenarioPublishTopicInUserSpace(scope.driver)
    }
}

fun testFavoriteTopicByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            val data = prepareFavoriteTopicScenario { target.createAuthenticatedSession(ports) }
            AppiumTestSetup(data = data, injectedSession = data.authenticated.session)
        },
    ) { scope, data ->
        try {
            scenarioFavoritePreparedTopic(scope.driver, data)
        } finally {
            data.authenticated.sessionManager.client.close()
        }
    }
}

fun testOpenAsciidocPreviewByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        captureBrowserOpen = platform.capturesExternalAsciidocPreview,
        setup = { ports ->
            val data = prepareAsciidocPreviewScenario { target.createAuthenticatedSession(ports) }
            AppiumTestSetup(data = data, injectedSession = data.authenticated.session)
        },
    ) { scope, data ->
        try {
            scenarioOpenAsciidocPreview(scope.driver, data.topicMarker)
            scope.assertAsciidocPreviewOpened(data.asciidocSource)
        } finally {
            data.authenticated.sessionManager.client.close()
        }
    }
}

fun testSubscribeTopicByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            val data = prepareSubscriptionTopicScenario { target.createAuthenticatedSession(ports) }
            AppiumTestSetup(data = data, injectedSession = data.authenticated.session)
        },
    ) { scope, data ->
        try {
            scenarioSubscribePreparedTopic(scope.driver, data)
        } finally {
            data.authenticated.sessionManager.client.close()
        }
    }
}

fun testCommunityProfileActionsByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = testPreparedCommunityRoomScenarioByHelper(testName, target, platform) { driver, data ->
    scenarioCommunityProfileActions(driver, data.communityName, data.ownerSession.address)
}

fun testPublishTopicInCommunityByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = testPreparedCommunityRoomScenarioByHelper(testName, target, platform) { driver, data ->
    scenarioPublishTopicInCommunity(driver, data.communityName)
}

fun testPublishTopicInCommunityRoomByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
) = testPreparedCommunityRoomScenarioByHelper(testName, target, platform) { driver, data ->
    scenarioPublishTopicInRoom(driver, data.communityName, data.roomName)
}

fun testPanelInjectedSessionByHelper(
    testName: String,
    target: PanelAppiumHelper,
    platform: PlatformAppiumHelper,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            val session = target.createPreRegisteredSession(ports)
            AppiumTestSetup(data = Unit, injectedSession = session)
        },
    ) { scope, _ ->
        scenarioOpenAllUsersFromOverview(scope.driver)
    }
}

private fun testPreparedCommunityRoomScenarioByHelper(
    testName: String,
    target: AppAppiumHelper,
    platform: PlatformAppiumHelper,
    block: suspend (AppTestDriver, PreparedCommunityRoomScenario) -> Unit,
) = runAppiumBlockingTest {
    platform.runTest(
        testName = testName,
        target = target,
        setup = { ports ->
            val data = prepareCommunityRoomScenario { target.createAuthenticatedSession(ports) }
            AppiumTestSetup(data = data, injectedSession = data.viewerSession)
        },
    ) { scope, data ->
        block(scope.driver, data)
    }
}
