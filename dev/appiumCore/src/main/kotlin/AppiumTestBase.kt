import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TestName
import kotlin.time.Duration.Companion.minutes

abstract class AppiumTestBase {
    @get:Rule
    val name = TestName()
}

fun runAppiumBlockingTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(10.minutes) {
        block()
    }
}
