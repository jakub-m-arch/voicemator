package io.github.jakub_m_arch.voicemator.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository.get(application)

    val isEnabled: StateFlow<Boolean> = repository.isEnabled

    /** Null until the first load finishes, so screens do not flash their empty state. */
    val transcriptions: StateFlow<List<Transcription>?> = repository.transcriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val statistics: StateFlow<UsageStatistics?> = repository.usage
        .map { entries ->
            val zone = ZoneId.systemDefault()
            UsageStatistics.compute(entries, LocalDate.now(zone), zone)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)

    fun delete(transcription: Transcription) {
        viewModelScope.launch { repository.delete(transcription.id) }
    }

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
