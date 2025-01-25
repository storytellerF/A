import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.storyteller_f.a.app.App
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.crypto_jvm.addProviderForJvm

fun main() {
    addProviderForJvm()
    restoreFromStorage()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "A",
        ) {
            App()
        }
    }
}
