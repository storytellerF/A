suspend fun scenarioSignUp(driver: AppTestDriver, privateKey: String) {
    driver.clickByDescription("avatar")
    driver.clickByText("Sign in")
    driver.clickByText("Go to sign up")
    driver.assertVisible(text = "Sign up")
    driver.clickByText("Private Key")
    driver.clickByDescription("Edit Private Key")
    driver.inputText(privateKey)
    driver.clickByText("Confirm")
    driver.clickByText("Start sign up")
    driver.assertVisible(description = "avatar")
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
    driver.assertNotVisible("Sign in")
}

suspend fun scenarioVerifyInjectedSessionLoaded(driver: AppTestDriver) {
    driver.clickByDescription("avatar")
    driver.assertNotVisible("Sign in")
}

suspend fun scenarioPublishTopicInUserSpace(driver: AppTestDriver, address: String, topicContent: String) {
    driver.clickByDescription("avatar")
    driver.clickByDescriptionContaining("user-dialog-cell")
    driver.clickByDescription("avatar")
    driver.clickByDescription("create")
    driver.clickByText("Raw")
    driver.inputText(topicContent)
    driver.clickByDescription("submit")
    driver.assertVisible(text = topicContent)
}

suspend fun scenarioFavoriteTopic(driver: AppTestDriver, address: String, topicContent: String) {
    driver.clickByDescription("avatar")
    driver.assertVisible(text = "ad: $address")
    driver.clickByDescription("user-dialog-cell")
    driver.assertVisible(text = topicContent)
    driver.clickByText(topicContent)
    driver.clickByDescription("topic")
    driver.clickByText("Favorite")
}

suspend fun scenarioOpenCommunity(driver: AppTestDriver, communityName: String) {
    driver.clickByText("Communities")
    driver.clickByText(communityName)
}

suspend fun scenarioSubscribeTopic(driver: AppTestDriver, communityName: String, topicContent: String) {
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText(topicContent)
    driver.clickByDescription("topic")
    driver.clickByText("Subscription")
}

suspend fun scenarioCommunityProfileActions(
    driver: AppTestDriver,
    communityName: String,
    ownerAddress: String,
) {
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText(communityName.first().toString())
    driver.clickByText("Favorite")
    driver.clickByText("Subscription")
    driver.clickByText("All members")
    driver.clickByTextContaining(ownerAddress)
}

suspend fun scenarioPublishTopicInCommunity(
    driver: AppTestDriver,
    communityName: String,
    topicContent: String,
) {
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText(communityName.first().toString())
    driver.clickByText("Add")
    driver.saveSnapshot("community-after-add")
    driver.clickByText("Raw")
    driver.inputText(topicContent)
    driver.clickByDescription("submit")
    driver.assertVisible(text = topicContent)
}

suspend fun scenarioPublishTopicInRoom(
    driver: AppTestDriver,
    communityName: String,
    roomName: String,
    topicContent: String,
) {
    scenarioOpenCommunity(driver, communityName)
    driver.clickByText("Rooms")
    driver.clickByText(roomName)
    driver.inputText(topicContent)
    driver.clickByDescription("Send")
    driver.assertVisible(text = topicContent)
}
