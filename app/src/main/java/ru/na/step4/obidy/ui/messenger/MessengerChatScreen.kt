package ru.na.step4.obidy.ui.messenger

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.na.step4.obidy.data.messenger.MessengerMessage
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.data.messenger.formatVoiceDuration
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerChatScreen(
    chatId: String,
    title: String,
    groupId: String,
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onGroupInfo: (String) -> Unit
) {
    val messages by viewModel.repository.messages(chatId).collectAsStateWithLifecycle(emptyList())
    val playingId by viewModel.playingId.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var cancelRecord by remember { mutableStateOf(false) }
    var recordMs by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val listState = rememberLazyListState()

    DisposableEffect(chatId) {
        viewModel.startChatPolling(chatId)
        onDispose { viewModel.stopChatPolling() }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(recording) {
        if (!recording) {
            recordMs = 0
            return@LaunchedEffect
        }
        val started = System.currentTimeMillis()
        while (recording) {
            recordMs = (System.currentTimeMillis() - started).toInt()
            delay(200)
        }
    }

    fun micGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun sendDraft() {
        val text = draft
        draft = ""
        viewModel.sendText(chatId, text)
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { MessengerRu.title }, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    if (groupId.isNotBlank()) {
                        IconButton(onClick = { onGroupInfo(groupId) }) {
                            Icon(Icons.Outlined.Info, MessengerRu.members, tint = Forest)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                        val prev = messages.getOrNull(index - 1)
                        if (prev == null || !sameDay(prev.createdAt, message.createdAt)) {
                            DayLabel(formatDayLabel(message.createdAt))
                        }
                        MessageBubble(
                            message = message,
                            playing = playingId == message.id,
                            showName = groupId.isNotBlank() && !message.mine,
                            onPlay = { viewModel.playVoice(message) }
                        )
                    }
                }
                if (recording) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(SandDeep)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${MessengerRu.recording} ${formatVoiceDuration(recordMs)}",
                            modifier = Modifier.weight(1f),
                            color = Forest
                        )
                        IconButton(onClick = { cancelRecord = true }) {
                            Icon(Icons.Outlined.Close, MessengerRu.cancelRecord, tint = Forest)
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Sand.copy(alpha = 0.96f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    VoiceOutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(MessengerRu.messageHint) },
                        maxLines = 4,
                        voiceEnabled = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendDraft() })
                    )
                    if (draft.isBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp, bottom = 4.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Forest)
                                .pointerInput(chatId) {
                                    detectTapGestures(
                                        onPress = {
                                            if (!micGranted()) {
                                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                return@detectTapGestures
                                            }
                                            cancelRecord = false
                                            recording = true
                                            runCatching { viewModel.repository.voiceRecorder.start() }
                                            tryAwaitRelease()
                                            recording = false
                                            val drop = cancelRecord
                                            val result = viewModel.repository.voiceRecorder.stop(delete = drop)
                                            if (result != null) {
                                                viewModel.sendVoice(chatId, result.first, result.second)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Mic, MessengerRu.voiceMessage, tint = Sand)
                        }
                    } else {
                        IconButton(onClick = { sendDraft() }) {
                            Icon(Icons.AutoMirrored.Outlined.Send, MessengerRu.send, tint = Forest)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayLabel(text: String) {
    if (text.isBlank()) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun MessageBubble(
    message: MessengerMessage,
    playing: Boolean,
    showName: Boolean,
    onPlay: () -> Unit
) {
    val mine = message.mine
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) Forest else SandDeep)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (showName && message.senderName.isNotBlank()) {
                Text(
                    message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mine) Sand.copy(alpha = 0.85f) else Forest
                )
                Spacer(Modifier.height(2.dp))
            }
            if (message.isVoice) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            MessengerRu.voiceMessage,
                            tint = if (mine) Sand else Forest
                        )
                    }
                    Text(
                        formatVoiceDuration(message.voiceDurationMs),
                        color = if (mine) Sand else Forest
                    )
                }
            } else {
                Text(
                    message.body,
                    color = if (mine) Sand else Forest,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                formatChatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) Sand.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
