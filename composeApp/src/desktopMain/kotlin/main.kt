import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composenotification.builder.AppConfig
import com.kdroid.composenotification.builder.NotificationInitializer
import com.storyteller_f.a.app.App
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.crypto_jvm.addProviderForJvm

fun main() {
    NotificationInitializer.configure(
        AppConfig(
            appName = "My awesome app",
        )
    )
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
