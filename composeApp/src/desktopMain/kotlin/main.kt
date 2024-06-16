import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.storyteller_f.a.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "A",
    ) {
        App()
    }
}
