package jvm_based

import com.storyteller_f.a.app.AppConfig
import com.storyteller_f.storage.loadKotbaseIfNeed
import org.junit.Assume
import org.junit.Before
import java.io.File

actual abstract class UsingContextTest {
    @Before
    fun setup() {
        Assume.assumeTrue(System.getProperty("os.name").orEmpty().contains("win", true))
        Assume.assumeNoException(kotlin.runCatching {
            System.load(File(AppConfig.PROJECT_PATH, "src/androidUnitTests/jniLibs/LiteCore.dll").absolutePath)
            System.load(File(AppConfig.PROJECT_PATH, "src/androidUnitTests/jniLibs/LiteCoreJNI.dll").absolutePath)
        }.exceptionOrNull())
        loadKotbaseIfNeed()
    }

    actual fun onActivity(block: () -> Unit) {
        block()
    }
}