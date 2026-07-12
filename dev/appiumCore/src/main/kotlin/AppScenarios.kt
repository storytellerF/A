suspend fun scenarioSignUp(driver: AppTestDriver) {
    val privateKey = generateAppiumPrivateKey()
    driver.clickByDescription("avatar")
    driver.clickByText("Sign in")
    driver.clickByText("Go to sign up")
    driver.assertVisibleByText("Sign up")
    driver.clickByText("Private Key")
    driver.clickByDescription("Edit Private Key")
    driver.inputText(privateKey)
    driver.clickByText("Confirm")
    driver.clickByText("Start sign up")
    driver.assertVisibleByDescription("avatar")
}

suspend fun scenarioSignIn(driver: AppTestDriver, privateKey: String) {
    driver.clickByDescription("avatar")
    driver.clickByText("Sign in")
    driver.clickByText("Private Key")
    driver.clickByDescription("Edit Private Key")
    driver.inputText(privateKey)
    driver.clickByText("Confirm")
    driver.clickByText("Start sign in")
    driver.clickByDescription("avatar")
    driver.assertNotVisibleByText("Sign in")
}

suspend fun scenarioSignInAsSystemUser(driver: AppTestDriver, privateKey: String) {
    scenarioSignIn(driver, privateKey)
    driver.assertNotVisibleByText("Sign in")
}

suspend fun scenarioVerifyInjectedSessionLoaded(driver: AppTestDriver) {
    driver.clickByDescription("avatar")
    driver.assertNotVisibleByText("Sign in")
}

suspend fun scenarioPublishTopicInUserSpace(driver: AppTestDriver, address: String) {
    val topicContent = "appium-user-space-topic-${System.currentTimeMillis()}"
    driver.clickByDescription("avatar")
    driver.clickByDescriptionContaining("user-dialog-cell")
    driver.clickByDescription("avatar")
    driver.clickByDescription("create")
    driver.clickByText("Raw")
    driver.inputText(topicContent)
    driver.clickByDescription("submit")
    driver.assertVisibleByText(topicContent)
}

suspend fun scenarioFavoriteTopic(driver: AppTestDriver, address: String, topicContent: String) {
    driver.clickByDescription("avatar")
    driver.clickByDescriptionContaining("user-dialog-cell")
    driver.assertVisibleByText(topicContent)
    driver.clickByText(topicContent)
    driver.clickByDescription("topic")
    driver.clickByDescriptionContaining("favorite-action")
}

suspend fun scenarioOpenCommunity(driver: AppTestDriver, communityName: String) {
    driver.clickByText("Communities")
    driver.clickByText(communityName)
}

suspend fun scenarioSubscribeTopic(driver: AppTestDriver, communityName: String, topicContent: String) {
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText(topicContent)
    driver.clickByDescription("topic")
    driver.clickByDescriptionContaining("subscribe-action")
}

suspend fun scenarioCommunityProfileActions(
    driver: AppTestDriver,
    communityName: String,
    ownerAddress: String,
) {
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText(communityName.first().toString())
    driver.clickByDescriptionContaining("favorite-action")
    driver.clickByDescriptionContaining("subscribe-action")
    driver.clickByDescriptionContaining("all-members-action")
    driver.clickByTextContaining(ownerAddress)
}

suspend fun scenarioPublishTopicInCommunity(
    driver: AppTestDriver,
    communityName: String,
) {
    val topicContent = "appium-community-topic-${System.currentTimeMillis()}"
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText(communityName.first().toString())
    driver.clickByDescriptionContaining("community-add-topic-action")
    driver.saveSnapshot("community-after-add")
    driver.clickByText("Raw")
    driver.inputText(topicContent)
    driver.clickByDescription("submit")
    driver.assertVisibleByText(topicContent)
}

suspend fun scenarioPublishTopicInRoom(
    driver: AppTestDriver,
    communityName: String,
    roomName: String,
) {
    val topicContent = "appium-room-topic-${System.currentTimeMillis()}"
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText("Rooms")
    driver.clickByTextContaining(roomName)
    driver.inputText(topicContent)
    driver.clickByDescription("Send")
    driver.assertVisibleByText(topicContent)
}
