package com.example.assistant.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.assistant.App
import com.example.assistant.R
import com.example.assistant.core.Assistant
import com.example.assistant.data.HistoryItem
import com.example.assistant.service.GestureAccessibilityService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(autoListen: Boolean, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()

    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Нажмите на микрофон") }
    var history by remember { mutableStateOf(app.history.load()) }

    fun begin() {
        listening = true
        status = "Слушаю…"
        scope.launch {
            app.assistant.startListening(object : Assistant.Listener {
                override fun onPartial(text: String) { scope.launch { status = text } }
                override fun onResult(text: String) {
                    scope.launch {
                        listening = false
                        if (text.isBlank()) { status = "Не расслышал. Попробуйте ещё раз."; return@launch }
                        status = "Думаю…"
                        val answer = app.assistant.answer(text)
                        status = answer
                        history = app.history.load()
                    }
                }
                override fun onError(message: String) {
                    scope.launch { listening = false; status = message }
                }
            })
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else status = "Нужно разрешение на микрофон"
    }

    fun onMic() {
        if (listening) {
            app.assistant.stopListening()
            listening = false
            status = "Нажмите на микрофон"
            return
        }
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) begin() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun screenAction(translate: Boolean) {
        val svc = GestureAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(ctx, "Включите службу доступности в настройках", Toast.LENGTH_LONG).show()
            return
        }
        (ctx as? Activity)?.moveTaskToBack(true)
        svc.runScreenAction(translate)
    }

    DisposableEffect(Unit) {
        onDispose { app.assistant.stopListening() }
    }
    DisposableEffect(autoListen) {
        if (autoListen) onMic()
        onDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ассистент", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.size(16.dp))
            Surface(
                onClick = { onMic() },
                shape = CircleShape,
                color = if (listening) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(132.dp),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_mic), contentDescription = "Микрофон",
                        tint = if (listening) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { screenAction(false) }, modifier = Modifier.weight(1f)) {
                    Text("Прочитать экран", maxLines = 1)
                }
                FilledTonalButton(onClick = { screenAction(true) }, modifier = Modifier.weight(1f)) {
                    Text("Перевести экран", maxLines = 1)
                }
            }
            Spacer(Modifier.size(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("История", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    TextButton(onClick = { app.history.clear(); history = emptyList() }) { Text("Очистить") }
                }
            }
            if (history.isEmpty()) {
                Text(
                    "Здесь появятся последние ответы.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { HistoryCard(it) }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem) {
    val time = remember(item.time) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.time)) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "$time · ${item.question}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(4.dp))
            Text(item.answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
