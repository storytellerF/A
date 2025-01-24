package jvm_based

import android.content.ContentProvider
import kotbase.CouchbaseLite
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    libraries = ["CouchbaseLiteTemp/1804786a0d7bbc395e8d4cbeb2342a1f/LiteCoreJNI.dll", "CouchbaseLiteTemp/1a4a585fd410b8ca6293a97ecb5a5c44/LiteCore.dll"]
)
actual abstract class UsingContextTest {
    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        CouchbaseLite.init(app, true)
        setupAndroidContextProvider()
    }

    // Configures Compose's AndroidContextProvider to access resources in tests.
    // See https://youtrack.jetbrains.com/issue/CMP-6612
    private fun setupAndroidContextProvider() {
        val type = findAndroidContextProvider() ?: return
        Robolectric.setupContentProvider(type)
    }

    private fun findAndroidContextProvider(): Class<ContentProvider>? {
        val providerClassName = "org.jetbrains.compose.resources.AndroidContextProvider"
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(providerClassName) as Class<ContentProvider>
        } catch (_: ClassNotFoundException) {
            println("Class not found: $providerClassName")
            // Tests that don't depend on Compose will not have the provider class in classpath and will get
            // ClassNotFoundException. Skip configuring the provider for them.
            null
        }
    }
}