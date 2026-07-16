interface AppTestDriver {
    suspend fun clickByDescription(description: String)
    suspend fun clickByText(text: String)
    suspend fun clickByTextContaining(text: String)
    suspend fun clickByDescriptionContaining(description: String)
    suspend fun inputText(text: String)
    suspend fun assertVisibleByDescription(description: String)
    suspend fun assertVisibleByText(text: String)
    suspend fun assertNotVisibleByDescription(description: String, timeoutSeconds: Long = 5)
    suspend fun assertNotVisibleByText(text: String, timeoutSeconds: Long = 5)
    suspend fun navigateBack()
    suspend fun saveSnapshot(name: String)
}
