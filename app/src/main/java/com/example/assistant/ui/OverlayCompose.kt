package com.example.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.assistant.R
import com.example.assistant.data.ThemeMode
import com.example.assistant.ui.theme.AssistantTheme

enum class Phase { IDLE, LISTENING, WORKING, ANSWER }

@Stable
class OverlayState {
    var visible by mutableStateOf(false)
    var phase by mutableStateOf(Phase.IDLE)
    var title by mutableStateOf("Ассистент")
    var body by mutableStateOf("")
}

interface OverlayActions {
    fun onMic()
    fun onReadScreen()
    fun onTranslateScreen()
    fun onClose()
}

@Composable
fun AssistantOverlay(state: OverlayState, actions: OverlayActions, themeMode: ThemeMode) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = state.visible

    AssistantTheme(themeMode) {
        AnimatedVisibility(
            visibleState = transition,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(260)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MicDot(active = state.phase == Phase.LISTENING)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            state.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = actions::onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                        }
                    }
                    if (state.body.isNotBlank()) {
                        Spacer(Modifier.size(6.dp))
                        Column(
                            Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                state.body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = actions::onMic,
                            modifier = Modifier.weight(1f)
                        ) { Text("Говорить", maxLines = 1) }
                        OutlinedButton(
                            onClick = actions::onReadScreen,
                            modifier = Modifier.weight(1f)
                        ) { Text("Экран", maxLines = 1) }
                        OutlinedButton(
                            onClick = actions::onTranslateScreen,
                            modifier = Modifier.weight(1f)
                        ) { Text("Перевод", maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** Small mic badge; pulses only while listening (and only exists while the overlay is composed). */
@Composable
fun MicDot(active: Boolean) {
    val scale = if (active) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            initialValue = 1f, targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "scale"
        ).value
    } else 1f
    Box(
        Modifier
            .size(36.dp)
            .scale(scale)
            .padding(0.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_mic), contentDescription = null,
                    tint = if (active) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
