import org.junit.Before

actual abstract class PlatformHeadlessTest {
    @Before
    fun setup() = Unit
    actual val portOffset: Int = 0
}
