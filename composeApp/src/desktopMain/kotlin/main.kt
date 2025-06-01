import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composenotification.builder.AppConfig
import com.kdroid.composenotification.builder.NotificationInitializer
import com.storyteller_f.a.app.App
import com.storyteller_f.shared.loadCryptoLibIfNeed
import java.awt.*
import kotlin.math.ceil
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

    val dpi: Int = Toolkit.getDefaultToolkit().screenResolution
    val uiScale = ceil(dpi.toFloat() / 100)
    println("Screen DPI: $dpi $uiScale")
    println(System.getProperty("sun.java2d.uiScale.enabled"))
    println(System.getProperty("sun.java2d.uiScale"))
    System.setProperty("sun.java2d.uiScale.enabled", "true")
    System.setProperty("sun.java2d.uiScale", "$uiScale")
//    UIManager.put("swing.boldMetal", "false")
//    System.setProperty("awt.useSystemAAFontSettings", "on")

    NotificationInitializer.configure(
        AppConfig(
            appName = "My awesome app",
        )
    )
    loadCryptoLibIfNeed()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "A",
        ) {
            App()
        }
    }
}
