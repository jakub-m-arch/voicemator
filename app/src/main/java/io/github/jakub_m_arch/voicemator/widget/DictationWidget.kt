package io.github.jakub_m_arch.voicemator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationStatus
import io.github.jakub_m_arch.voicemator.dictation.DictationTriggerActivity

/**
 * Home screen widget that toggles dictation, like the Quick Settings tile. At its smallest it
 * shows only the mic; wider, it also says what dictation is doing.
 */
class DictationWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, views(context, DictationStatus.state.value))
    }

    companion object {
        /** Redraws every placed widget for [state]. */
        fun update(context: Context, state: DictationState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, DictationWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, state))
        }

        private fun views(context: Context, state: DictationState) = RemoteViews(
            mapOf(
                SizeF(MIN_SIZE_DP, MIN_SIZE_DP) to layout(context, state, showLabel = false),
                SizeF(LABEL_MIN_WIDTH_DP, MIN_SIZE_DP) to layout(context, state, showLabel = true),
            ),
        )

        private fun layout(context: Context, state: DictationState, showLabel: Boolean): RemoteViews {
            val isActive = state != DictationState.Idle
            val content = if (isActive) R.color.widget_content_active else R.color.widget_content_idle
            val label = context.getString(
                when (state) {
                    DictationState.Idle -> R.string.tile_label
                    DictationState.Recording -> R.string.widget_recording
                    DictationState.Transcribing -> R.string.tile_subtitle_transcribing
                },
            )
            val intent = PendingIntent.getActivity(
                context,
                0,
                DictationTriggerActivity.intent(context),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_dictate).apply {
                setInt(
                    android.R.id.background,
                    "setBackgroundResource",
                    if (isActive) R.drawable.widget_background_active else R.drawable.widget_background_idle,
                )
                setColorStateList(R.id.widget_icon, "setImageTintList", content)
                setColorStateList(R.id.widget_label, "setTextColor", content)
                setTextViewText(R.id.widget_label, label)
                setViewVisibility(R.id.widget_label, if (showLabel) View.VISIBLE else View.GONE)
                setContentDescription(android.R.id.background, label)
                setOnClickPendingIntent(android.R.id.background, intent)
            }
        }

        private const val MIN_SIZE_DP = 40f
        private const val LABEL_MIN_WIDTH_DP = 110f
    }
}
