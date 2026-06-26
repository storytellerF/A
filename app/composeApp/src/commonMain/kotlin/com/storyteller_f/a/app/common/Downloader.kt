package com.storyteller_f.a.app.common

import com.storyteller_f.a.app.UIViewModel
import com.storyteller_f.a.client.core.SimpleUserSessionManager
import com.storyteller_f.a.client.core.serviceCatching
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.nowInstance
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStatus
import com.storyteller_f.storage.ModelStorage
import no.synth.kmpzip.kotlinx.unzipFrom
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime

interface Downloader {
    fun download(fileInfo: FileInfo)

    fun resume(id: PrimaryKey)

    fun pause(id: PrimaryKey)
}

interface TaskRegister {
    fun lockTask(key: String, block: suspend () -> Unit)
    fun stop(key: String, block: suspend () -> Unit)
}

class SimpleTaskRegister(val lifecycleScope: CoroutineScope) : TaskRegister {
    val runningTaskCount = MutableStateFlow(0)
    val mutex = Mutex()
    val runningSet = mutableMapOf<String, Job>()

    override fun lockTask(key: String, block: suspend () -> Unit) {
        lifecycleScope.launch {
            lock(key, block)
        }
    }

    override fun stop(key: String, block: suspend () -> Unit) {
        lifecycleScope.launch {
            runningSet[key]?.cancelAndJoin()
            block()
        }
    }

    suspend inline fun lock(id: String, block: suspend () -> Unit) {
        val job = currentCoroutineContext()[Job]
        if (job == null) {
            Napier.w(tag = "download") {
                "no job in lock"
            }
            return
        }
        mutex.withLock {
            if (runningSet.contains(id)) {
                return
            }
            runningTaskCount.update {
                it + 1
            }
            runningSet[id] = job
        }
        try {
            block()
        } finally {
            mutex.withLock {
                runningTaskCount.update {
                    it - 1
                }
                runningSet.remove(id)
            }
        }
    }
}

class DownloaderImpl(
    val uiViewModel: UIViewModel,
    val register: TaskRegister
) : Downloader {
    override fun download(fileInfo: FileInfo) {
        val path = Path(SystemTemporaryDirectory, "downloads", fileInfo.id.toString(), fileInfo.name)
        Napier.i(tag = "download") {
            "download $fileInfo to $path"
        }
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        val instance = uiViewModel.instance.value
        val modelStorage = instance.database
        val userSession = instance.sessionManager
        register.lockTask(fileInfo.id.toString()) {
            try {
                downloadIfNeed(modelStorage, fileInfo, path, userSession)
            } catch (e: Exception) {
                Napier.e(e, tag = "download") {
                    "download failed"
                }
            }
        }
    }

    override fun resume(id: PrimaryKey) {
        Napier.i(tag = "download") {
            "resume download $id"
        }
        val instance = uiViewModel.instance.value
        val modelStorage = instance.database
        val userSession = instance.sessionManager
        register.lockTask(id.toString()) {
            try {
                download(userSession, modelStorage, id)
            } catch (e: Exception) {
                Napier.e(e, tag = "download") {
                    "resume failed"
                }
            }
        }
    }

    override fun pause(id: PrimaryKey) {
        Napier.i(tag = "download") {
            "pause download $id"
        }
        val instance = uiViewModel.instance.value
        val modelStorage = instance.database
        pauseIfNeed(id, modelStorage)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun downloadIfNeed(
        modelStorage: ModelStorage,
        fileInfo: FileInfo,
        path: Path,
        userSession: SimpleUserSessionManager
    ) {
        val document = modelStorage.download.getDocumentByFileId(fileInfo.id)
        if (document == null) {
            val new = DownloadInfo(
                nowInstance().epochSeconds,
                fileInfo,
                DownloadStatus.NOT_DOWNLOADED,
                "",
                path.toString(),
                0,
                fileInfo.size
            )
            modelStorage.download.save(new)
            Napier.i(tag = "download") {
                "add new document"
            }
        } else if (document.status != DownloadStatus.NOT_DOWNLOADED) {
            Napier.i(tag = "download") {
                "current download status ${document.status}"
            }
            return
        }
        download(userSession, modelStorage, fileInfo.id)
        return
    }

    private fun pauseIfNeed(
        id: PrimaryKey,
        modelStorage: ModelStorage,
    ) {
        register.stop(id.toString()) {
            updateDownloadInfo(modelStorage, id) {
                it.copy(status = DownloadStatus.PAUSED)
            }
        }
    }

    private suspend fun download(
        userSession: SimpleUserSessionManager,
        modelStorage: ModelStorage,
        id: PrimaryKey,
    ) {
        val downloadInfo = modelStorage.download.getDocumentByFileId(id)
        if (downloadInfo == null) {
            Napier.w(tag = "download") {
                "no downloadInfo"
            }
            return
        }
        if (downloadInfo.status == DownloadStatus.PROCESSED) return
        val path = Path(downloadInfo.path)
        val isNeedDownload =
            if (downloadInfo.status == DownloadStatus.DOWNLOADED ||
                downloadInfo.status == DownloadStatus.PROCESS_FAILED
            ) {
                val isFileExists = SystemFileSystem.exists(path)
                val isMediaSizeMatch = SystemFileSystem.metadataOrNull(path)?.size == downloadInfo.total
                !isFileExists || !isMediaSizeMatch
            } else {
                true
            }
        if (isNeedDownload) {
            val fileInfo = downloadInfo.fileInfo
            if (downloadFile(downloadInfo, modelStorage, id, userSession, fileInfo, path)) {
                return
            }
        }
        if (path.toString().endsWith(".zip")) {
            if (extractFile(path, modelStorage, id)) return
        }
        updateDownloadInfo(modelStorage, id) {
            it.copy(status = DownloadStatus.PROCESSED)
        }
    }

    private suspend fun extractFile(
        path: Path,
        modelStorage: ModelStorage,
        id: PrimaryKey
    ): Boolean {
        try {
            SystemFileSystem.unzipFrom(
                archive = path,
                target = Path(path.parent!!, "${path.name}.extracted"),
            )
        } catch (e: Exception) {
            updateDownloadInfo(modelStorage, id) {
                it.copy(status = DownloadStatus.PROCESS_FAILED, message = e.message.toString())
            }
            return true
        }
        return false
    }

    /**
     * @return 返回是否下载成功
     */
    private suspend fun downloadFile(
        downloadInfo: DownloadInfo,
        modelStorage: ModelStorage,
        id: PrimaryKey,
        userSession: SimpleUserSessionManager,
        fileInfo: FileInfo,
        path: Path
    ): Boolean {
        if (downloadInfo.status != DownloadStatus.DOWNLOADING) {
            updateDownloadInfo(modelStorage, id) {
                it.copy(status = DownloadStatus.DOWNLOADING)
            }
        }
        try {
            userSession.serviceCatching {
                segmentedDownload(modelStorage, fileInfo, path)
            }.getOrThrow()
            updateDownloadInfo(modelStorage, id) {
                it.copy(
                    status = DownloadStatus.DOWNLOADED,
                    message = "download success ${now()}"
                )
            }
            return false
        } catch (throwable: Exception) {
            if (throwable is CancellationException) {
                Napier.e(throwable) {
                    "download failed ${fileInfo.name}"
                }
                updateDownloadInfo(modelStorage, id) {
                    it.copy(status = DownloadStatus.DOWNLOAD_FAILED, message = throwable.message.toString())
                }
            }
            return true
        }
    }

    suspend fun HttpClient.segmentedDownload(
        modelStorage: ModelStorage,
        fileInfo: FileInfo,
        path: Path
    ) {
        var downloadedBytes = 0L

        // Check if the file already exists and get its size to resume the download
        if (SystemFileSystem.exists(path)) {
            downloadedBytes = SystemFileSystem.metadataOrNull(path)?.size ?: 0L
        }

        prepareGet(fileInfo.url) {
            // Set the Range header to request the remaining part of the file
            header(HttpHeaders.Range, "bytes=$downloadedBytes-")
        }.execute { httpResponse ->
            // Check for successful partial or full content
            val httpStatus = httpResponse.status
            if (httpStatus != HttpStatusCode.OK && httpStatus != HttpStatusCode.PartialContent) {
                throw Exception("Download failed: ${httpStatus.description}")
            }

            val channel: ByteReadChannel = httpResponse.body()

            // Open the sink in append mode to continue writing to the file
            SystemFileSystem.sink(path, append = true).use { sink ->
                while (!channel.isClosedForRead) {
                    val chunk = channel.readRemaining(10240)
                    if (chunk.exhausted()) continue

                    downloadedBytes += chunk.remaining
                    chunk.transferTo(sink)

                    updateDownloadInfo(modelStorage, fileInfo.id) {
                        it.copy(progress = downloadedBytes)
                    }
                    Napier.i {
                        "download progress $downloadedBytes"
                    }
                }
            }
        }
    }

    suspend fun updateDownloadInfo(
        modelStorage: ModelStorage,
        id: PrimaryKey,
        block: (DownloadInfo) -> DownloadInfo
    ) {
        val uploadInfo = modelStorage.download.getDocumentByFileId(id) ?: return
        modelStorage.download.save(block(uploadInfo))
    }
}
