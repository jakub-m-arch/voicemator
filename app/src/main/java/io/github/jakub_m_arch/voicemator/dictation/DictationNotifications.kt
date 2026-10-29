package io.github.jakub_m_arch.voicemator.dictation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import io.github.jakub_m_arch.voicemator.MainActivity
import io.github.jakub_m_arch.voicemator.R

/** Builds the notifications shown while dictating and after a transcription is ready. */
class DictationNotifications(private val context: Context) {

    private val notificationManager = checkNotNull(context.getSystemService<NotificationManager>())

    fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_DICTATION,
                    context.getString(R.string.channel_dictation),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_RESULTS,
                    context.getString(R.string.channel_results),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    fun recording(): Notification =
        ongoing(R.string.notification_recording)
            .addAction(0, context.getString(R.string.action_stop), serviceIntent(DictationService.stopIntent(context)))
            .addAction(0, context.getString(R.string.action_cancel), serviceIntent(DictationService.cancelIntent(context)))
            .build()

    fun transcribing(): Notification =
        ongoing(R.string.notification_transcribing)
            .setProgress(0, 0, true)
            .build()

    fun showCopied(text: String) = showResult(
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setContentTitle(context.getString(R.string.notification_copied))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)),
    )

    fun showNothingHeard() = showResult(
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setContentTitle(context.getString(R.string.notification_nothing_heard)),
    )

    fun showFailure(error: Throwable) = showResult(
        NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setContentTitle(context.getString(R.string.notification_failed))
            .setContentText(error.localizedMessage),
    )

    private fun showResult(builder: NotificationCompat.Builder) {
        val notification = builder
            .setSmallIcon(R.drawable.ic_dictate)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MILLIS)
            .build()
        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    fun update(notification: Notification) {
        notificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
    }

    private fun ongoing(titleRes: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_DICTATION)
            .setSmallIcon(R.drawable.ic_dictate)
            .setContentTitle(context.getString(titleRes))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    private fun serviceIntent(intent: Intent): PendingIntent =
        PendingIntent.getService(context, intent.action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ONGOING_NOTIFICATION_ID = 1

        private const val RESULT_NOTIFICATION_ID = 2
        private const val CHANNEL_DICTATION = "dictation"
        private const val CHANNEL_RESULTS = "results"
        private const val RESULT_TIMEOUT_MILLIS = 15_000L
    }
}
