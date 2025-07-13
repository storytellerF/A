import com.storyteller_f.storage.loadKotbaseIfNeed
import org.junit.Assume
import org.junit.Before
import java.io.File

actual abstract class UsingContextTest {
    @Before
    fun setup() {
        loadKotbaseIfNeed()
    }

    actual fun onActivity(block: () -> Unit) {
        block()
    }

    actual fun executeIfNeed() {
    }
}