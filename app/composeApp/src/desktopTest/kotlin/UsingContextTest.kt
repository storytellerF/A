import org.junit.Before

actual abstract class UsingContextTest {
    @Before
    fun setup() {
    }

    actual fun onActivity(block: () -> Unit) {
        block()
    }

    actual fun executeIfNeed() {
    }
}