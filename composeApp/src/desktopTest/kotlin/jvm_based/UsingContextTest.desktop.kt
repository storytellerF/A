package jvm_based

import com.storyteller_f.a.app.BuildKonfig
import kotbase.CouchbaseLite
import org.junit.Before
import java.io.File

actual abstract class UsingContextTest {
    @Before
    fun setup() {
        System.load(File(BuildKonfig.PROJECT_PATH, "src/desktopTest/jniLibs/LiteCore.dll").absolutePath)
        System.load(File(BuildKonfig.PROJECT_PATH, "src/desktopTest/jniLibs/LiteCoreJNI.dll").absolutePath)
        CouchbaseLite.init()
    }
}