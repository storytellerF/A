suspend fun scenarioOpenAllUsersFromOverview(driver: AppTestDriver) {
    driver.assertVisibleByText("Overview")
    driver.clickByDescription("Menu")
    driver.clickByText("All users")
    driver.assertVisibleByText("All users")
}
