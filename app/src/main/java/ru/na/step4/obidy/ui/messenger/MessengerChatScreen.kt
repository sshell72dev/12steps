package ru.na.step4.obidy.ui.messenger

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.alerts.AppAlerts
import ru.na.step4.obidy.data.messenger.MessengerMessage
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.data.messenger.formatVoiceDuration
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.step4.obidy.ui.update.UpdateCentre
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerChatScreen(
    chatId: String,
    title: String,
    groupId: String,
    avatarUrl: String = "",
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onGroupInfo: (String) -> Unit,
    onTopics: (String) -> Unit = {},
    onOpenAlert: (MessengerMessage) -> Unit = {}
) {
    val messages by viewModel.repository.messages(chatId).collectAsStateWithLifecycle(emptyList())
    val playingId by viewModel.playingId.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var cancelRecord by remember { mutableStateOf(false) }
    var recordMs by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<MessengerMessage?>(null) }
    var editFor by remember { mutableStateOf<MessengerMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deleteFor by remember { mutableStateOf<MessengerMessage?>(null) }
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val listState = rememberLazyListState()
    val alertsChat = chatId == AppAlerts.CHAT_ID

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

    menuFor?.let { target ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(MessengerRu.messageActions) },
            text = {
                Column {
                    if (!target.isVoice) {
                        TextButton(onClick = {
                            editDraft = target.body
                            editFor = target
                            menuFor = null
                        }) { Text(MessengerRu.messageEdit, color = Forest) }
                    }
                    TextButton(onClick = {
                        deleteFor = target
                        menuFor = null
                    }) { Text(MessengerRu.messageDelete, color = Forest) }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuFor = null }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }
    editFor?.let { target ->
        AlertDialog(
            onDismissRequest = { editFor = null },
            title = { Text(MessengerRu.messageEditTitle) },
            text = {
                VoiceOutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    placeholder = { Text(MessengerRu.messageHint) },
                    maxLines = 6,
                    voiceEnabled = false
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    val text = editDraft
                    editFor = null
                    viewModel.editMessage(id, text)
                }) { Text(MessengerRu.save, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { editFor = null }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }
    deleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(MessengerRu.messageDelete) },
            text = { Text(MessengerRu.messageDeleteQuestion) },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    deleteFor = null
                    viewModel.deleteMessage(id)
                }) { Text(MessengerRu.confirmYes, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (avatarUrl.isNotBlank() && !alertsChat) {
                            MessengerAvatar(
                                avatarUrl = avatarUrl,
                                title = title,
                                size = 34.dp,
                                viewModel = viewModel
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                        Text(title.ifBlank { MessengerRu.title }, color = Forest)
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    if (groupId.isNotBlank() && !alertsChat) {
                        IconButton(onClick = { onTopics(groupId) }) {
                            Icon(Icons.Outlined.MenuBook, MessengerRu.topicsOpen, tint = Forest)
                        }
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
                            showName = (groupId.isNotBlank() || alertsChat) && !message.mine,
                            onPlay = { viewModel.playVoice(message) },
                            onOpen = if (alertsChat &&
                                AppAlerts.resolveTarget(
                                    message.senderId,
                                    message.body,
                                    message.senderName
                                ).isNotBlank()
                            ) {
                                { onOpenAlert(message) }
                            } else {
                                null
                            },
                            onLongPress = if (
                                !alertsChat &&
                                message.mine &&
                                !message.deleted &&
                                !message.isUpdate
                            ) {
                                { menuFor = message }
                            } else {
                                null
                            }
                        )
                    }
                    if (alertsChat && messages.isEmpty()) {
                        item {
                            Text(
                                MessengerRu.alertsHow,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                            )
                        }
                    }
                }
                if (!alertsChat) {
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
    onPlay: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
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
                .then(
                    if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
                )
                .then(
                    if (onLongPress != null) {
                        Modifier.pointerInput(message.id) {
                            detectTapGestures(onLongPress = { onLongPress?.invoke() })
                        }
                    } else {
                        Modifier
                    }
                )
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
            if (message.deleted) {
                Text(
                    MessengerRu.messageDeleted,
                    color = if (mine) Sand.copy(alpha = 0.75f) else Forest.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (message.isVoice) {
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
            if (message.isUpdate) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                Spacer(Modifier.height(8.dp))
                Text(
                    MessengerRu.updateNow,
                    color = Sand,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Forest)
                        .clickable {
                            scope.launch { UpdateCentre.check(context) }
                        }
                        .padding(vertical = 10.dp)
                )
            }
            Text(
                if (message.isEdited) {
                    "${MessengerRu.messageEdited} · ${formatMessageTime(message.createdAt)}"
                } else {
                    formatMessageTime(message.createdAt)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) Sand.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
