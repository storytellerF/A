interface AppTestDriver {
    suspend fun clickByDescription(description: String)
    suspend fun clickByText(text: String)
    suspend fun clickByTextContaining(text: String)
    suspend fun inputText(text: String)
    suspend fun assertVisible(description: String? = null, text: String? = null)
    suspend fun assertNotVisible(text: String, timeoutSeconds: Long = 5)
    suspend fun navigateBack()
    suspend fun saveSnapshot(name: String)
}
