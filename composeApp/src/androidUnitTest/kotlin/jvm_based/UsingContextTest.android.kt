package jvm_based

import android.content.ComponentName
import android.content.ContentProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.storyteller_f.a.app.MainActivity
import kotbase.CouchbaseLite
import org.junit.Assume
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
)
actual abstract class UsingContextTest {
    @Before
    fun setup() {
        Assume.assumeTrue(System.getProperty("os.name").orEmpty().contains("win", true))
        System.loadLibrary("LiteCore")
        System.loadLibrary("LiteCoreJNI")
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(
            ComponentName(
                app.packageName,
                MainActivity::class.simpleName!!,
            )
        )
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

    actual fun onActivity(block: () -> Unit) {
        // GIVEN
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // WHEN
        scenario.moveToState(Lifecycle.State.CREATED)

        // THEN
        scenario.onActivity {
            block()
        }
    }
}