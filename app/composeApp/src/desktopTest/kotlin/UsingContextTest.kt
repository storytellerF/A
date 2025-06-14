import com.storyteller_f.storage.loadKotbaseIfNeed
import org.junit.Assume
import org.junit.Before
import java.io.File

actual abstract class UsingContextTest {
    @Before
    fun setup() {
        Assume.assumeTrue(System.getProperty("os.name").orEmpty().contains("win", true))
        val e = runCatching {
            System.load(File("src/androidUnitTest/jniLibs/LiteCore.dll").absolutePath)
            System.load(File("src/androidUnitTest/jniLibs/LiteCoreJNI.dll").absolutePath)
        }.exceptionOrNull()
        e?.printStackTrace()
        Assume.assumeNoException(e)
        loadKotbaseIfNeed()
    }

    actual fun onActivity(block: () -> Unit) {
        block()
    }

    actual fun executeIfNeed() {
    }
}