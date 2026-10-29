package io.github.jakub_m_arch.voicemator.model

import android.app.DownloadManager
import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val progress: Float) : ModelState
    data object Installed : ModelState
    data object Failed : ModelState
}

/**
 * Downloads the model through the system [DownloadManager], so large downloads survive the app
 * being closed and show progress in the notification shade.
 *
 * Download IDs are persisted, which lets the app pick up progress after a restart. Finished
 * downloads are verified and moved into place by [currentState], which may therefore take a few
 * seconds and must not be called on the main thread.
 */
class ModelDownloader(context: Context) {

    private val appContext = context.applicationContext
    private val storage = ModelStorage(appContext)
    private val downloadManager = checkNotNull(appContext.getSystemService<DownloadManager>())
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Enqueues every model file that is neither complete nor already being downloaded. */
    fun start() {
        storage.deleteOtherModels()
        ParakeetModel.files
            .filterNot { storage.isComplete(it) || isInProgress(it) }
            .forEach(::enqueue)
    }

    fun cancel() {
        val ids = ParakeetModel.files.mapNotNull(::downloadId).toLongArray()
        if (ids.isNotEmpty()) downloadManager.remove(*ids)
        preferences.edit { clear() }
    }

    @WorkerThread
    fun currentState(): ModelState {
        if (storage.isInstalled()) {
            preferences.edit { clear() }
            return ModelState.Installed
        }

        var downloadedBytes = 0L
        var anyInProgress = false
        var anyFailed = false

        for (file in ParakeetModel.files) {
            if (storage.isComplete(file)) {
                downloadedBytes += file.sizeBytes
                continue
            }
            val download = downloadId(file)?.let(::queryDownload) ?: continue
            when (download.status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED -> {
                    anyInProgress = true
                    downloadedBytes += download.bytesDownloaded
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    forgetDownload(file)
                    if (storage.install(file)) downloadedBytes += file.sizeBytes else anyFailed = true
                }
                else -> anyFailed = true
            }
        }

        if (storage.isInstalled()) return ModelState.Installed
        return when {
            anyInProgress -> ModelState.Downloading(downloadedBytes.toFloat() / ParakeetModel.totalSizeBytes)
            anyFailed -> ModelState.Failed
            else -> ModelState.NotInstalled
        }
    }

    private fun enqueue(file: ModelFile) {
        // DownloadManager renames instead of overwriting, so remove leftovers of a broken download.
        storage.partialFileFor(file).delete()

        val request = DownloadManager.Request(ParakeetModel.downloadUrl(file).toUri())
            .setTitle(file.name)
            .setDescription(ParakeetModel.ID)
            .setDestinationInExternalFilesDir(appContext, null, storage.partialRelativePath(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val id = downloadManager.enqueue(request)
        preferences.edit { putLong(file.name, id) }
    }

    private fun isInProgress(file: ModelFile): Boolean {
        val status = downloadId(file)?.let(::queryDownload)?.status ?: return false
        return status != DownloadManager.STATUS_SUCCESSFUL && status != DownloadManager.STATUS_FAILED
    }

    private fun forgetDownload(file: ModelFile) {
        preferences.edit { remove(file.name) }
    }

    private fun downloadId(file: ModelFile): Long? =
        preferences.getLong(file.name, NO_ID).takeIf { it != NO_ID }

    private fun queryDownload(id: Long): DownloadInfo? =
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            DownloadInfo(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                bytesDownloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                ),
            )
        }

    private data class DownloadInfo(val status: Int, val bytesDownloaded: Long)

    private companion object {
        const val PREFERENCES_NAME = "model_downloads"
        const val NO_ID = -1L
    }
}
