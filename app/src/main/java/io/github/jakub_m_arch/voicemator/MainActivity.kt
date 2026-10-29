package io.github.jakub_m_arch.voicemator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.jakub_m_arch.voicemator.ui.VoicematorApp
import io.github.jakub_m_arch.voicemator.ui.theme.VoicematorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoicematorTheme {
                VoicematorApp()
            }
        }
    }
}
