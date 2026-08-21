/*
 * This is a private project. All rights reserved.
*/

package com.storyteller_f.a.dev.appium

suspend fun scenarioOpenAllUsersFromOverview(driver: AppTestDriver) {
    driver.assertVisibleByText("Overview")
    driver.clickByDescription("Menu")
    driver.clickByText("All users")
    driver.assertVisibleByText("All users")
}
