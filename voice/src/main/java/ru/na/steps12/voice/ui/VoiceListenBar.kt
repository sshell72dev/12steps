package ru.na.steps12.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.steps12.voice.VoicePlugin
import ru.na.steps12.voice.VoiceRu

/**
 * Панель прослушки: пауза, перемотка на минуту и вход с выбранной минуты.
 * У TTS нет своей шкалы времени — позиция считается по нарезанному тексту.
 */
@Composable
fun VoiceListenBar(
    plugin: VoicePlugin,
    modifier: Modifier = Modifier
) {
    val speaker = plugin.speaker
    val speaking by speaker.speaking.collectAsStateWithLifecycle()
    val paused by speaker.paused.collectAsStateWithLifecycle()
    val position by speaker.positionMs.collectAsStateWithLifecycle()
    val duration by speaker.durationMs.collectAsStateWithLifecycle()
    // Панель имеет смысл для длинной прослушки: короткую озвучку можно поставить
    // на паузу кнопкой у текста, а минутами в ней перематывать нечего.
    val longEnough = duration >= MIN_LISTEN_BAR_MS
    if ((!speaking || !longEnough) && !paused) return
    var minute by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { plugin.speaker.pauseOrResume() }) {
                    Icon(
                        imageVector = if (speaking) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (speaking) VoiceRu.pause else VoiceRu.resume,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { plugin.skipSpeakingMinutes(-1) }) {
                    Text(VoiceRu.minuteBack)
                }
                TextButton(onClick = { plugin.skipSpeakingMinutes(1) }) {
                    Text(VoiceRu.minuteForward)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${listenTime(position)} / ${listenTime(duration)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { plugin.stopSpeaking() }) {
                    Icon(
                        imageVector = Icons.Outlined.StopCircle,
                        contentDescription = VoiceRu.stop,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = minute,
                    onValueChange = { input -> minute = input.filter { it.isDigit() }.take(3) },
                    label = { Text(VoiceRu.minuteFrom) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { plugin.seekSpeakingMinute(minute.toIntOrNull() ?: 1) }) {
                    Text(VoiceRu.minuteStart)
                }
            }
        }
    }
}

/** Ниже этой длительности панель не показываем: перематывать по минутам нечего. */
private const val MIN_LISTEN_BAR_MS = 60_000L

private fun listenTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
