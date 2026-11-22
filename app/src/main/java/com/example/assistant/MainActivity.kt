package com.example.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import com.example.assistant.data.ThemeMode
import com.example.assistant.ui.MainScreen
import com.example.assistant.ui.SettingsScreen
import com.example.assistant.ui.theme.AssistantTheme

class MainActivity : ComponentActivity() {

    companion object { const val EXTRA_LISTEN = "listen" }

    private var listenRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        listenRequest = intent?.getBooleanExtra(EXTRA_LISTEN, false) == true
        setContent {
            var theme by remember { mutableStateOf(App.instance.settings.themeMode) }
            var showSettings by remember { mutableStateOf(false) }
            AssistantTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showSettings) {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            onBack = { showSettings = false },
                            onThemeChanged = { m: ThemeMode -> theme = m }
                        )
                    } else {
                        MainScreen(autoListen = listenRequest, onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        listenRequest = intent.getBooleanExtra(EXTRA_LISTEN, false)
    }
}
