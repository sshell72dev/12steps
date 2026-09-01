package ru.na.steps12.voice.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import ru.na.steps12.voice.VoiceI18n
import ru.na.steps12.voice.VoiceRu

@Composable
fun VoiceFieldActions(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    voiceEnabled: Boolean = true,
    speakEnabled: Boolean = true,
    append: Boolean = true,
    selection: TextRange = TextRange(value.length),
    onDictated: ((spoken: String, at: TextRange) -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null
) {
    if (!voiceEnabled && !speakEnabled && extra == null) return
    Row {
        extra?.invoke()
        if (speakEnabled) {
            SpeakIconButton(text = value, enabled = enabled && value.isNotBlank())
        }
        if (voiceEnabled) {
            DictationIconButton(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                append = append,
                selection = selection,
                onDictated = onDictated
            )
        }
    }
}

@Composable
fun DictationIconButton(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    append: Boolean = true,
    selection: TextRange = TextRange(value.length),
    onDictated: ((spoken: String, at: TextRange) -> Unit)? = null,
    tint: Color = LocalContentColor.current
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(false) }
    // Capture cursor at the moment mic is pressed — not after speech returns.
    var insertAt by remember { mutableStateOf(selection) }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pending = false
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (spoken.isBlank()) return@rememberLauncherForActivityResult
        if (onDictated != null) {
            onDictated(spoken, insertAt)
            return@rememberLauncherForActivityResult
        }
        onValueChange(mergeSpoken(value, spoken, append, insertAt))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pending = true
            insertAt = selection
            runCatching { speechLauncher.launch(speechIntent()) }
        } else {
            pending = false
        }
    }
    IconButton(
        onClick = {
            insertAt = selection
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                pending = true
                runCatching { speechLauncher.launch(speechIntent()) }
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = if (pending) VoiceRu.listening else VoiceRu.dictation,
            tint = if (pending) Color(0xFFB8893D) else tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

internal fun mergeSpoken(
    value: String,
    spoken: String,
    append: Boolean,
    selection: TextRange
): String {
    if (!append) return spoken
    val start = selection.min.coerceIn(0, value.length)
    val end = selection.max.coerceIn(0, value.length)
    val before = value.substring(0, start)
    val after = value.substring(end)
    val piece = buildString {
        if (before.isNotEmpty() && !before.last().isWhitespace()) append(' ')
        append(spoken)
        if (after.isNotEmpty() && !after.first().isWhitespace()) append(' ')
    }
    return before + piece + after
}

internal fun mergeSpokenField(
    field: TextFieldValue,
    spoken: String,
    append: Boolean
): TextFieldValue {
    if (!append) {
        return TextFieldValue(text = spoken, selection = TextRange(spoken.length))
    }
    val start = field.selection.min.coerceIn(0, field.text.length)
    val end = field.selection.max.coerceIn(0, field.text.length)
    val before = field.text.substring(0, start)
    val after = field.text.substring(end)
    val piece = buildString {
        if (before.isNotEmpty() && !before.last().isWhitespace()) append(' ')
        append(spoken)
        if (after.isNotEmpty() && !after.first().isWhitespace()) append(' ')
    }
    val newText = before + piece + after
    // After dictation, place cursor at the end of the whole message.
    return TextFieldValue(text = newText, selection = TextRange(newText.length))
}

@Composable
fun SpeakIconButton(
    text: String,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    val plugin = LocalVoicePlugin.current
    val idle = remember { MutableStateFlow(false) }
    val speaking by (plugin?.speaker?.speaking ?: idle).collectAsStateWithLifecycle()
    IconButton(
        onClick = {
            if (plugin == null) return@IconButton
            if (speaking) plugin.stopSpeaking() else plugin.speak(text)
        },
        enabled = enabled && (plugin != null) && (speaking || text.isNotBlank()),
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = if (speaking) Icons.Outlined.StopCircle else Icons.Outlined.VolumeUp,
            contentDescription = if (speaking) VoiceRu.stop else VoiceRu.speak,
            tint = if (speaking) Color(0xFFB8893D) else tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun speechIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceI18n.speechTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, VoiceI18n.speechTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, VoiceRu.listening)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8_000L)
    }
