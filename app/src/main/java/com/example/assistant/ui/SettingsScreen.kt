package com.example.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.assistant.App
import com.example.assistant.BuildConfig
import com.example.assistant.data.CornerSide
import com.example.assistant.data.ThemeMode
import com.example.assistant.service.GestureAccessibilityService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChanged: (ThemeMode) -> Unit) {
    val ctx = LocalContext.current
    val app = App.instance
    val s = app.settings
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(s.enabled) }
    var serviceOn by remember { mutableStateOf(GestureAccessibilityService.instance != null) }
    var micOn by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(s.themeMode) }
    var side by remember { mutableStateOf(s.cornerSide) }
    var size by remember { mutableStateOf(s.cornerSizeDp.toFloat()) }
    var backLp by remember { mutableStateOf(s.backLongPress) }
    var speak by remember { mutableStateOf(s.speak) }
    var wifi by remember { mutableStateOf(s.downloadOnlyWifi) }
    var foreign by remember { mutableStateOf(s.foreignLang) }
    var ruReady by remember { mutableStateOf(false) }
    var foreignReady by remember { mutableStateOf(false) }
    var transMsg by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(s.cloudBaseUrl) }
    var model by remember { mutableStateOf(s.cloudModel) }
    var cloudOn by remember { mutableStateOf(false) }

    fun refreshPerms() {
        serviceOn = GestureAccessibilityService.instance != null
        micOn = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    LifecycleResumeEffect(Unit) { refreshPerms(); onPauseOrDispose {} }

    LaunchedEffect(Unit) {
        val k = app.secrets.getApiKey()
        cloudOn = k.isNotBlank()
    }

    suspend fun refreshModels() {
        try {
            ruReady = app.translation.isDownloaded("ru")
            foreignReady = app.translation.isDownloaded(foreign)
        } catch (_: Exception) { transMsg = "Нужны сервисы Google Play для перевода." }
    }
    LaunchedEffect(foreign) { refreshModels() }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPerms() }

    fun openAccessibility() {
        ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("Ассистент") {
                RowSwitch(
                    "Ассистент включён",
                    if (enabled) "Жесты активны" else "Служба выключена, память освобождена",
                    enabled
                ) { on ->
                    enabled = on
                    s.enabled = on
                    if (!on) {
                        GestureAccessibilityService.instance?.shutdown()
                        serviceOn = false
                    } else if (GestureAccessibilityService.instance == null) openAccessibility()
                }
            }

            Section("Разрешения") {
                PermRow("Микрофон", micOn) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                PermRow("Служба доступности", serviceOn) { openAccessibility() }
                Text(
                    "Android 13+: если пункт серый, откройте «О приложении» → ⋮ → «Разрешить ограниченные настройки».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("О приложении") }
            }

            Section("Вызов") {
                Text("Угол для свайпа вверх", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CornerSide.LEFT to "Слева", CornerSide.RIGHT to "Справа", CornerSide.BOTH to "Оба").forEach { (v, l) ->
                        FilterChip(selected = side == v, onClick = {
                            side = v; s.cornerSide = v
                            GestureAccessibilityService.instance?.refreshZones()
                        }, label = { Text(l) })
                    }
                }
                Text("Размер зоны: ${size.toInt()} dp", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = size, onValueChange = { size = it },
                    valueRange = 48f..140f,
                    onValueChangeFinished = {
                        s.cornerSizeDp = size.toInt()
                        GestureAccessibilityService.instance?.refreshZones()
                    }
                )
                RowSwitch("Долгое нажатие «Назад»", "Работает с кнопкой «Назад» (3 кнопки или аппаратная)", backLp) {
                    backLp = it; s.backLongPress = it
                }
            }

            Section("Внешний вид и голос") {
                Text("Тема", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to "Системная", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная").forEach { (v, l) ->
                        FilterChip(selected = theme == v, onClick = {
                            theme = v; s.themeMode = v; onThemeChanged(v)
                        }, label = { Text(l) })
                    }
                }
                RowSwitch("Озвучивать ответы", "Системный синтез речи, включается на время фразы", speak) {
                    speak = it; s.speak = it
                }
            }

            Section("Распознавание текста (OCR)") {
                Text(
                    "Режим этой сборки: ${BuildConfig.OCR_MODE}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "unbundled — модель в Google Play Services (экономит память и размер APK). " +
                        "bundled — модель внутри APK (гарантированно офлайн). Переключается при сборке: " +
                        "-PocrMode=bundled или запуск workflow вручную с параметром. " +
                        "ML Kit распознаёт латиницу; русский текст читается напрямую из интерфейса приложений.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section("Перевод") {
                Text("Язык в паре с русским", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.translation.supported.forEach { (code, name) ->
                        FilterChip(selected = foreign == code, onClick = {
                            foreign = code; s.foreignLang = code
                        }, label = { Text(name) })
                    }
                }
                RowSwitch("Скачивать только по Wi-Fi", "", wifi) { wifi = it; s.downloadOnlyWifi = it }
                ModelRow("Русский", ruReady,
                    onDownload = { scope.launch { runDownload("ru", wifi, { transMsg = it }) { refreshModels() } } },
                    onDelete = { scope.launch { runDelete("ru", { transMsg = it }) { refreshModels() } } })
                ModelRow(app.translation.supported.firstOrNull { it.first == foreign }?.second ?: foreign, foreignReady,
                    onDownload = { scope.launch { runDownload(foreign, wifi, { transMsg = it }) { refreshModels() } } },
                    onDelete = { scope.launch { runDelete(foreign, { transMsg = it }) { refreshModels() } } })
                if (transMsg.isNotBlank()) Text(transMsg, style = MaterialTheme.typography.bodyMedium)
            }

            Section("Облачная модель (необязательно)") {
                Text(
                    if (cloudOn) "Онлайн-режим включён: вопросы вне локальных команд уходят на сервер."
                    else "Выключено. Без ключа приложение не обращается в интернет.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text(if (cloudOn) "Новый API-ключ" else "API-ключ") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Base URL (OpenAI-совместимый)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Модель") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        s.cloudBaseUrl = url.trim(); s.cloudModel = model.trim()
                        if (apiKey.isNotBlank()) { app.secrets.setApiKey(apiKey); cloudOn = true; apiKey = "" }
                    }) { Text("Сохранить") }
                    OutlinedButton(onClick = { app.secrets.setApiKey(""); cloudOn = false; apiKey = "" }) {
                        Text("Удалить ключ")
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

private suspend fun runDownload(code: String, wifiOnly: Boolean, msg: (String) -> Unit, done: suspend () -> Unit) {
    msg("Скачиваю модель…")
    try {
        App.instance.translation.download(code, wifiOnly)
        msg("Готово.")
    } catch (e: Exception) {
        msg("Не удалось скачать: ${e.message ?: "проверьте сеть (Wi-Fi) и Google Play"}")
    }
    done()
}

private suspend fun runDelete(code: String, msg: (String) -> Unit, done: suspend () -> Unit) {
    try { App.instance.translation.delete(code); msg("Модель удалена.") }
    catch (e: Exception) { msg("Ошибка: ${e.message}") }
    done()
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun RowSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank())
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermRow(title: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (granted) "Выдано" else "Не выдано",
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        }
        if (!granted) Button(onClick = onGrant) { Text("Выдать") }
    }
}

@Composable
private fun ModelRow(name: String, ready: Boolean, onDownload: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (ready) "Скачан" else "Не скачан",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (ready) OutlinedButton(onClick = onDelete) { Text("Удалить") }
        else Button(onClick = onDownload) { Text("Скачать") }
    }
}
