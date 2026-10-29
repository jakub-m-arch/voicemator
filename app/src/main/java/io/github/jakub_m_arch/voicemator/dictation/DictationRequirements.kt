package io.github.jakub_m_arch.voicemator.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.jakub_m_arch.voicemator.model.ModelStorage

/** Conditions that must hold before dictation can start without user interaction. */
object DictationRequirements {

    fun hasMicrophonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun areMet(context: Context): Boolean =
        hasMicrophonePermission(context) && ModelStorage(context).isInstalled()
}
