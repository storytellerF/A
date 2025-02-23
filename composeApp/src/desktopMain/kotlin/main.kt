import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composenotification.builder.AppConfig
import com.kdroid.composenotification.builder.NotificationInitializer
import com.storyteller_f.a.app.App
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.crypto_jvm.addProviderForJvm
import java.awt.Toolkit
import javax.swing.UIManager

fun main() {
    if (!com.storyteller_f.a.app.AppConfig.IS_PROD) {
        val dpi: Int = Toolkit.getDefaultToolkit().screenResolution
        println("Screen DPI: $dpi")
        System.setProperty("sun.java2d.uiScale.enabled", "true")
        System.setProperty("sun.java2d.uiScale", "${dpi / 100}")
        UIManager.put("swing.boldMetal", "false")
        System.setProperty("awt.useSystemAAFontSettings", "on")
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
