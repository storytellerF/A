package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.awtClipboard
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.storyteller_f.shared.type.PrimaryKey
import com.strabled.composepreferences.utilis.DataStoreManager
import dev.jordond.connectivity.Connectivity
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.utils.toFile
import io.ktor.http.ContentType
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import okio.Path.Companion.toOkioPath
import org.apache.tika.Tika
import java.awt.datatransfer.StringSelection
import java.io.File

val tika = Tika()

actual val appPlatform: AppPlatform
    get() = AppPlatform(false, debug = true)

actual fun initEnvironment(context: Any) = Unit

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setText(string: String) {
    awtClipboard?.setContents(StringSelection(string), null)
}

actual fun createConnectivity(): Connectivity {
    return Connectivity {
        autoStart = true
        urls("cloudflare.com")
        port = 80
        pollingIntervalMs = 10.minutes
        timeoutMs = 5.seconds
    }
}

actual fun getClientFile(path: String): ClientFile? {
    val file = File(path)
    return RegularClientFile(file)
}

actual fun startCall(roomId: PrimaryKey) = Unit

class RegularClientFile(val file: File) : ClientFile {
    override val name: String = file.name
    override val contentType: ContentType = ContentType.parse(tika.detect(file))
    override val size: Long = file.length()
    override val path: String = file.path

    override fun source(): Source {
        return file.inputStream().asSource().buffered()
    }
}

private val store by lazy {
    DataStoreManager(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                val pb = Path(SystemTemporaryDirectory, "com.storyteller_f.a.app.compose_app.main.preferences_pb")
                val file = pb.toFile()
                if (!file.exists() && !file.createNewFile()) {
                    Napier.e {
                        "$file create failed"
                    }
                }
                file.toOkioPath()
            }
        )
    )
}

@Composable
actual fun createCustomDataStoreManager(): DataStoreManager {
    return store
}

actual fun unregisterPushService() = Unit
