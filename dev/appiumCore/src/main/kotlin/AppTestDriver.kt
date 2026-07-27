interface AppTestDriver {
    suspend fun clickByDescription(description: String)

    /** Opens the avatar shown by the current user-detail surface. */
    suspend fun clickUserDetailAvatar()

    suspend fun clickByText(text: String)
    suspend fun clickByTextContaining(text: String)
    suspend fun inputText(text: String)
    suspend fun assertVisibleByDescription(description: String)
    suspend fun assertVisibleByText(text: String)

    /** Verifies that visible UI text contains [text]. */
    suspend fun assertVisibleByTextContaining(text: String)

    suspend fun assertNotVisibleByText(text: String, timeoutSeconds: Long = 5)
    suspend fun navigateBack()
    suspend fun saveSnapshot(name: String)
}
