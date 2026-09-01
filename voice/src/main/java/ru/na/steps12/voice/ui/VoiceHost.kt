package ru.na.steps12.voice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import ru.na.steps12.voice.VoicePlugin

val LocalVoicePlugin = staticCompositionLocalOf<VoicePlugin?> { null }

@Composable
fun VoiceHost(
    plugin: VoicePlugin,
    content: @Composable () -> Unit
) {
    DisposableEffect(plugin) {
        plugin.speaker.ensureReady()
        onDispose { }
    }
    CompositionLocalProvider(LocalVoicePlugin provides plugin) {
        content()
    }
}
