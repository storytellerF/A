import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composenotification.builder.AppConfig
import com.kdroid.composenotification.builder.NotificationInitializer
import com.storyteller_f.a.app.App
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.crypto_jvm.addProviderForJvm
import java.awt.*
import kotlin.system.exitProcess

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        Dialog(Frame(), e.message ?: "Error").apply {
            layout = BorderLayout()
            val label = TextArea(e.stackTraceToString())
            add(label, BorderLayout.CENTER)
            val button = Button("OK").apply {
                addActionListener {
                    dispose()
                    exitProcess(1)
                }
            }
            add(button, BorderLayout.SOUTH)
            setSize(300, 300)
            isVisible = true
        }
    }

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
