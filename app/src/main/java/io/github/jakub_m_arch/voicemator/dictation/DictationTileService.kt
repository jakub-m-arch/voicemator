package io.github.jakub_m_arch.voicemator.dictation

import android.app.PendingIntent
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.edit
import io.github.jakub_m_arch.voicemator.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile that toggles dictation. */
class DictationTileService : TileService() {

    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope = MainScope().apply {
            launch { DictationStatus.state.collect(::render) }
        }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        setAdded(this, true)
    }

    override fun onTileRemoved() {
        setAdded(this, false)
        super.onTileRemoved()
    }

    override fun onClick() {
        super.onClick()
        when (DictationStatus.state.value) {
            DictationState.Idle -> if (isLocked) unlockAndRun(::launchDictation) else launchDictation()
            // The app already runs a foreground service, so it may start services from here.
            DictationState.Recording -> startService(DictationService.stopIntent(this))
            DictationState.Transcribing -> Unit
        }
    }

    private fun launchDictation() {
        val intent = DictationTriggerActivity.intent(this)
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }

    private fun render(state: DictationState) {
        val tile = qsTile ?: return
        tile.state = if (state == DictationState.Idle) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.subtitle = when (state) {
            DictationState.Idle -> null
            DictationState.Recording -> getString(R.string.tile_subtitle_recording)
            DictationState.Transcribing -> getString(R.string.tile_subtitle_transcribing)
        }
        tile.updateTile()
    }

    companion object {
        private const val PREFERENCES_NAME = "tile"
        private const val KEY_ADDED = "added"

        /** Android cannot be asked whether a tile is added, so the tile records it itself. */
        fun isAdded(context: Context): Boolean =
            preferences(context).getBoolean(KEY_ADDED, false)

        fun setAdded(context: Context, added: Boolean) {
            preferences(context).edit { putBoolean(KEY_ADDED, added) }
        }

        private fun preferences(context: Context) =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
}
