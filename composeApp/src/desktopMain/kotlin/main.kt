import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.storyteller_f.a.app.App
import com.storyteller_f.a.app.restoreFromStorage
import com.storyteller_f.shared.addProvider

fun main() {
    addProvider()
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
