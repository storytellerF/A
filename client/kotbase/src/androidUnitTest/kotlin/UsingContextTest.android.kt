import com.couchbase.lite.CouchbaseLite
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.intArrayOf

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
actual abstract class UsingContextTest {
    @Before
    fun setup() {
        System.loadLibrary("LiteCore")
        System.loadLibrary("LiteCoreJNI")
        val application = RuntimeEnvironment.getApplication()
//        CouchbaseLite.init(true)
        CouchbaseLite.init(application, true)
    }
}
