package ru.na.steps12.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.steps12.voice.VoiceCatalog
import ru.na.steps12.voice.VoicePlugin
import ru.na.steps12.voice.VoiceRu
import ru.na.steps12.voice.VoiceSettingsStore
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsPanel(
    plugin: VoicePlugin,
    modifier: Modifier = Modifier
) {
    val cfg by plugin.config.collectAsStateWithLifecycle()
    val ttsReady by plugin.speaker.ready.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val selected = cfg.option
    val deviceVoices = remember(ttsReady) { plugin.speaker.deviceVoiceOptions() }
    val options = remember(deviceVoices) { VoiceCatalog.all + deviceVoices }

    LaunchedEffect(Unit) { plugin.speaker.ensureReady() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(VoiceRu.settingsTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            VoiceRu.settingsHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!cfg.configured) {
            Text(
                VoiceRu.notConfigured,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text(VoiceRu.voiceLabel, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected.label,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            plugin.store.setVoice(option)
                            expanded = false
                            plugin.speak(VoiceRu.previewText)
                        }
                    )
                }
            }
        }
        Text(
            "${VoiceRu.speedLabel}: ${String.format(Locale.US, "%.1f", cfg.speed)}×",
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = cfg.speed,
            onValueChange = { plugin.store.setSpeed(it) },
            valueRange = VoiceSettingsStore.MIN_SPEED..VoiceSettingsStore.MAX_SPEED,
            steps = 12
        )
        TextButton(onClick = { plugin.speak(VoiceRu.previewText) }) {
            Text(VoiceRu.preview)
        }
    }
}
