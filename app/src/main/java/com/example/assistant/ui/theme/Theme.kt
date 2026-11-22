package com.example.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.assistant.data.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Indigo40,
    primaryContainer = IndigoContainerLight,
    secondary = Teal40,
    background = SurfaceLight,
    surface = SurfaceLight
)

private val DarkScheme = darkColorScheme(
    primary = Indigo80,
    primaryContainer = IndigoContainerDark,
    secondary = Teal80,
    background = SurfaceDark,
    surface = SurfaceDark
)

@Composable
fun AssistantTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
