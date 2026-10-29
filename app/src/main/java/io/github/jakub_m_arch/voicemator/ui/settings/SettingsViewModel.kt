package io.github.jakub_m_arch.voicemator.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationStatus
import io.github.jakub_m_arch.voicemator.model.ModelDownloader
import io.github.jakub_m_arch.voicemator.model.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = ModelDownloader(application)
    private val mutableModelState = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    private var pollingJob: Job? = null

    val modelState: StateFlow<ModelState> = mutableModelState.asStateFlow()
    val dictationState: StateFlow<DictationState> = DictationStatus.state

    fun refreshModelState() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            // DownloadManager has no progress callbacks, so poll while a download is running.
            do {
                val state = withContext(Dispatchers.IO) { downloader.currentState() }
                mutableModelState.value = state
                if (state is ModelState.Downloading) delay(PROGRESS_POLL_INTERVAL_MILLIS)
            } while (state is ModelState.Downloading)
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloader.start() }
            refreshModelState()
        }
    }

    fun cancelDownload() {
        pollingJob?.cancel()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloader.cancel() }
            refreshModelState()
        }
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MILLIS = 500L
    }
}
