package ru.na.steps12.voice.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.na.steps12.voice.VoicePlugin

val LocalVoicePlugin = staticCompositionLocalOf<VoicePlugin?> { null }

/**
 * Панель прослушки живёт поверх любого экрана, но движок TTS здесь не поднимается:
 * `TextToSpeech` биндится к системному сервису и добавлял секунды к холодному старту,
 * хотя большинство запусков ничего не слушает. `speaker.speak()` сам создаёт движок
 * при первом воспроизведении.
 */
@Composable
fun VoiceHost(
    plugin: VoicePlugin,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalVoicePlugin provides plugin) {
        Box(Modifier.fillMaxSize()) {
            content()
            // Панель прослушки поверх экрана: пауза и перемотка доступны в любом месте.
            VoiceListenBar(
                plugin = plugin,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }
}
