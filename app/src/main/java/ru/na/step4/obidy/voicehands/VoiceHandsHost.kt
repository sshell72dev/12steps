package ru.na.step4.obidy.voicehands

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.LocalVoicePlugin

@Composable
fun VoiceHandsHost(
    onOpenPsych: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Step4App
    val settings = app.voiceHandsSettings
    val plugin = LocalVoicePlugin.current ?: return
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    LaunchedEffect(enabled) {
        if (enabled &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            settings.setEnabled(false)
        }
    }
    val controller = remember(settings, plugin) {
        VoiceHandsController(
            context = context,
            settings = settings,
            speaker = plugin.speaker,
            openPsych = onOpenPsych
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.onForeground()
                Lifecycle.Event.ON_STOP -> controller.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val window = (context as? Activity)?.window
    DisposableEffect(enabled, window) {
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val ui by controller.ui.collectAsStateWithLifecycle()
    if (!enabled && ui.phase == VoiceHandsPhase.Off) return
    VoiceHandsOverlay(
        ui = ui,
        onStandby = controller::returnToStandby,
        onDisable = controller::disable,
        modifier = modifier
    )
}

@Composable
fun VoiceHandsSettingsPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = (context.applicationContext as Step4App).voiceHandsSettings
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        settings.setEnabled(granted)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(VoiceHandsRu.experiment, color = Amber, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                VoiceHandsRu.title,
                modifier = Modifier.weight(1f),
                color = Forest,
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (!on) {
                        settings.setEnabled(false)
                        return@Switch
                    }
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) settings.setEnabled(true)
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Forest)
            )
        }
        Text(
            VoiceHandsRu.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            VoiceHandsRu.commands,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun VoiceHandsOverlay(
    ui: VoiceHandsUi,
    onStandby: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = when {
        ui.phase == VoiceHandsPhase.Dictating && ui.draft.isNotBlank() -> ui.draft
        ui.lastHeard.isNotBlank() -> ui.lastHeard
        else -> ""
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Forest.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            "${VoiceHandsRu.experiment} · ${ui.status}",
            color = Amber,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            if (ui.listening) VoiceHandsRu.listening else ui.status,
            color = Sand,
            style = MaterialTheme.typography.titleMedium
        )
        if (preview.isNotBlank()) {
            Text(
                preview,
                color = Sand.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3
            )
        }
        if (!ui.error.isNullOrBlank()) {
            Text(ui.error, color = Amber, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (ui.phase != VoiceHandsPhase.Standby && ui.phase != VoiceHandsPhase.Off) {
                TextButton(onClick = onStandby) {
                    Text(VoiceHandsRu.toStandby, color = Sand)
                }
            }
            TextButton(onClick = onDisable) {
                Text(VoiceHandsRu.disable, color = Amber)
            }
        }
    }
}
