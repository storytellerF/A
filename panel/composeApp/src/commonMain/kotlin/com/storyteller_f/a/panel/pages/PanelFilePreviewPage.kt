import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.core.components.AudioViewFullScreen
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.PdfView
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.VideoViewFullScreen
import com.storyteller_f.a.panel.common.createPanelFileViewModel

@Composable
fun PanelFilePreviewPage(id: Long) {
    val fileInfoViewModel = createPanelFileViewModel(id)
    Surface {
        StateView(fileInfoViewModel.handler, modifier = Modifier.fillMaxSize()) {
            val contentType = it.contentType
            val name = it.name
            val url = it.url
            when {
                contentType.startsWith("video") -> {
                    val item = RemoteMediaItem(
                        id = id.toString(),
                        url = url,
                        contentType = contentType,
                        isM3U8PlayList = false,
                        name = name
                    )
                    VideoViewFullScreen(item)
                }

                contentType.startsWith("audio") -> {
                    val item = RemoteMediaItem(
                        id = id.toString(),
                        url = url,
                        contentType = contentType,
                        isM3U8PlayList = false,
                        name = name
                    )
                    AudioViewFullScreen(item)
                }

                contentType.startsWith("image") -> {
                    com.github.panpf.zoomimage.CoilZoomAsyncImage(
                        model = com.storyteller_f.a.app.core.components.globalLoader(url),
                        contentDescription = "view image",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                contentType == com.storyteller_f.shared.model.FileInfo.PDF_CONTENT_TYPE -> {
                    PdfView(url, Modifier.fillMaxSize())
                }

                else -> {
                    CenterBox {
                        Text("Unsupported content type: $contentType")
                    }
                }
            }
        }
    }
}
