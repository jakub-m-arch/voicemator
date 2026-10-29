package io.github.jakub_m_arch.voicemator.licenses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.jakub_m_arch.voicemator.ui.theme.VoicematorTheme

class LicensesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoicematorTheme {
                LicensesScreen(onClose = ::finish)
            }
        }
    }
}
