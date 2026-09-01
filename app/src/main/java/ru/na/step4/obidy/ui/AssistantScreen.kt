package ru.na.step4.obidy.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.assistant.ChatTurn
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.SpeakIconButton
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onBack: () -> Unit,
    onOpenResentment: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoice()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    fun requestVoice() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.session.turns.size) {
        if (state.session.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.session.turns.lastIndex)
        }
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Ru.assistantTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = Forest
                        )
                        Text(
                            text = if (state.questionAssist.active) {
                                state.questionAssist.focusTitle
                            } else {
                                Ru.assistantStep.format(state.session.funnelStep.key)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber,
                            maxLines = 2
                        )
                    }
                },
                navigationIcon = {
                    AppNavIcon(onBack = {
                        viewModel.stopVoice()
                        onBack()
                    })
                },
                actions = {
                    NoteView(
                        NoteIds.ASSISTANT,
                        if (state.questionAssist.active) Ru.assistantFocusHint else Ru.assistantHint,
                        compact = true
                    )
                    if (!state.questionAssist.active && state.session.draftTarget.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.createResentmentFromDraft(onOpenResentment)
                        }) {
                            Icon(Icons.Outlined.Save, contentDescription = Ru.saveDraft, tint = Forest)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.imeScaffoldContent(padding)
        ) {
            AtmosphereBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                VoiceBar(
                    inCall = state.voice.inCall,
                    connecting = state.voice.connecting,
                    muted = state.voice.muted,
                    configured = state.voice.configured,
                    error = state.voice.lastError,
                    onStart = { requestVoice() },
                    onStop = viewModel::stopVoice,
                    onMute = viewModel::toggleMute
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.session.turns, key = { "${it.at}-${it.role}-${it.content.hashCode()}" }) { turn ->
                        ChatBubble(turn)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    VoiceOutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(Ru.assistantInputHint) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Forest,
                            cursorColor = Forest
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = viewModel::sendText) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = Ru.send,
                            tint = Forest
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceBar(
    inCall: Boolean,
    connecting: Boolean,
    muted: Boolean,
    configured: Boolean,
    error: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMute: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Forest.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inCall) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Danger,
                        contentColor = Sand
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Ru.voiceEnd)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onMute) {
                    Icon(
                        if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        contentDescription = Ru.voiceMute,
                        tint = Forest
                    )
                }
            } else if (connecting) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Forest.copy(alpha = 0.55f),
                        contentColor = Sand
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Ru.voiceConnecting)
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = configured,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Forest,
                        contentColor = Sand,
                        disabledContainerColor = Forest.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Ru.voiceStart)
                }
            }
        }
        if (!configured) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                Ru.voiceNotConfigured,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
        }
    }
}

@Composable
private fun ChatBubble(turn: ChatTurn) {
    val mine = turn.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) Forest else Sand.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = turn.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) Sand else Forest,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
            SpeakIconButton(
                text = turn.content,
                tint = if (mine) Sand else Forest
            )
        }
    }
}
