package com.storyteller_f.a.app.compose_app.common

import com.storyteller_f.a.app.compose_app.CustomUserSessionManager
import com.storyteller_f.a.app.compose_app.UIViewModel
import com.storyteller_f.a.client.core.serviceCatching
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.storage.DownloadCollection
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStatus
import com.storyteller_f.storage.ModelStorage
import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.extractTo
import de.jonasbroeckmann.kzip.open
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.use

interface Downloader {
    fun download(fileInfo: FileInfo, path: Path)

    fun resume(fileInfo: FileInfo)
}

class DownloaderImpl(val lifecycleScope: CoroutineScope, val uiViewModel: UIViewModel) :
    Downloader {
    val mutex = Mutex()
    val runningSet = mutableSetOf<PrimaryKey>()
    override fun download(fileInfo: FileInfo, path: Path) {
        Napier.i {
            "download $fileInfo to $path"
        }
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        val instance = uiViewModel.instance.value
        val modelStorage = instance.database.value
        val userSession = instance.sessionManager
        lifecycleScope.launch {
            downloadIfNeed(userSession, path, fileInfo, modelStorage)
        }
    }

    override fun resume(fileInfo: FileInfo) {
        Napier.i {
            "resume download $fileInfo"
        }
        val instance = uiViewModel.instance.value
        val modelStorage = instance.database.value
        val userSession = instance.sessionManager
        lifecycleScope.launch {
            resumeIfNeed(fileInfo, modelStorage, userSession)
        }
    }

    private suspend fun resumeIfNeed(
        fileInfo: FileInfo,
        modelStorage: ModelStorage,
        userSession: CustomUserSessionManager
    ) {
        val document =
            modelStorage.download.getDocument(DownloadCollection, fileInfo.id) ?: return
        download(userSession, modelStorage, fileInfo, Path(document.path))
    }

    private suspend fun downloadIfNeed(
        userSession: CustomUserSessionManager,
        path: Path,
        fileInfo: FileInfo,
        modelStorage: ModelStorage,
    ) {
        lock(fileInfo.id) {
            val document =
                modelStorage.download.getDocument(DownloadCollection, fileInfo.id)
            if (document == null) {
                val new = DownloadInfo(
                    fileInfo,
                    DownloadStatus.NOT_DOWNLOADED,
                    "",
                    path.toString(),
                    0,
                    fileInfo.size
                )
                modelStorage.download.save(DownloadCollection, new)
            } else if (document.status != DownloadStatus.NOT_DOWNLOADED) {
                return
            }
            download(userSession, modelStorage, fileInfo, path)
        }
    }

    private suspend fun download(
        userSession: CustomUserSessionManager,
        modelStorage: ModelStorage,
        fileInfo: FileInfo,
        path: Path,
    ) {
        val id = fileInfo.id
        val downloadInfo =
            modelStorage.download.getDocument(DownloadCollection, id) ?: return
        if (downloadInfo.status == DownloadStatus.PROCESSED) return
        val isNeedDownload =
            if (downloadInfo.status == DownloadStatus.DOWNLOADED ||
                downloadInfo.status == DownloadStatus.PROCESS_FAILED
            ) {
                val isFileExists = SystemFileSystem.exists(path)
                val isMediaSizeMatch = SystemFileSystem.metadataOrNull(path)?.size == fileInfo.size
                !isFileExists || !isMediaSizeMatch
            } else {
                true
            }
        if (isNeedDownload) {
            if (downloadFile(downloadInfo, modelStorage, id, userSession, fileInfo, path)) return
        }
        if (path.toString().endsWith(".zip")) {
            if (extractFile(path, modelStorage, id)) return
        }
        updateDownloadInfo(modelStorage, DownloadCollection, id) {
            it.copy(status = DownloadStatus.PROCESSED)
        }
    }

    private suspend fun extractFile(
        path: Path,
        modelStorage: ModelStorage,
        id: PrimaryKey
    ): Boolean {
        try {
            Zip.open(path).use { zip ->
                zip.extractTo(Path(path.parent!!, "${path.name}.extracted"))
            }
        } catch (e: Exception) {
            updateDownloadInfo(modelStorage, DownloadCollection, id) {
                it.copy(status = DownloadStatus.PROCESS_FAILED, message = e.message.toString())
            }
            return true
        }
        return false
    }

    private suspend fun downloadFile(
        downloadInfo: DownloadInfo,
        modelStorage: ModelStorage,
        id: PrimaryKey,
        userSession: CustomUserSessionManager,
        fileInfo: FileInfo,
        path: Path
    ): Boolean {
        if (downloadInfo.status != DownloadStatus.DOWNLOADING) {
            updateDownloadInfo(modelStorage, DownloadCollection, id) {
                it.copy(status = DownloadStatus.DOWNLOADING)
            }
        }
        try {
            userSession.serviceCatching {
                segmentedDownload(modelStorage, fileInfo, path)
            }.getOrThrow()
        } catch (throwable: Exception) {
            Napier.e(throwable) {
                "download failed ${fileInfo.fullName}"
            }
            updateDownloadInfo(modelStorage, DownloadCollection, id) {
                it.copy(
                    status = DownloadStatus.DOWNLOAD_FAILED,
                    message = throwable.message.toString()
                )
            }
            return true
        }
        updateDownloadInfo(modelStorage, DownloadCollection, id) {
            it.copy(
                status = DownloadStatus.DOWNLOADED,
                message = "download success ${now()}"
            )
        }
        return false
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

                    updateDownloadInfo(modelStorage, DownloadCollection, fileInfo.id) {
                        it.copy(progress = downloadedBytes)
                    }
                }
            }
        }
    }

    suspend fun updateDownloadInfo(
        modelStorage: ModelStorage,
        collection: DownloadCollection,
        id: PrimaryKey,
        block: (DownloadInfo) -> DownloadInfo
    ) {
        val uploadInfo = modelStorage.download.getDocument(collection, id) ?: return
        modelStorage.download.save(collection, block(uploadInfo))
    }

    suspend inline fun lock(id: PrimaryKey, block: suspend () -> Unit) {
        mutex.withLock {
            if (runningSet.contains(id)) {
                return
            }
            runningSet.add(id)
        }
        try {
            block()
        } finally {
            mutex.withLock {
                runningSet.remove(id)
            }
        }
    }
}
