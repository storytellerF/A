suspend fun scenarioOpenAllUsersFromOverview(driver: AppTestDriver) {
    driver.assertVisible(text = "Overview")
    driver.clickByDescription("Menu")
    driver.clickByText("All users")
    driver.assertVisible(text = "All users")
}
