package ru.na.step4.obidy.ui.messenger

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.alerts.AppAlerts
import ru.na.step4.obidy.data.messenger.MessengerAnon
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
    var deleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var replyTo by remember { mutableStateOf<MessengerMessage?>(null) }
    var forwardIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val myName by viewModel.repository.displayName.collectAsStateWithLifecycle()
    val chats by viewModel.repository.chats.collectAsStateWithLifecycle(emptyList())
    val pinnedMessage by viewModel.repository.pinned.collectAsStateWithLifecycle()
    val pinnedChatId by viewModel.repository.pinnedChatId.collectAsStateWithLifecycle()
    val pinned = pinnedMessage.takeIf { pinnedChatId == chatId }
    val isAdmin = viewModel.repository.isAdmin
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val listState = rememberLazyListState()
    /** Нажатие на шапку закрепа прокручивает ленту к этому сообщению. */
    val pinnedScope = rememberCoroutineScope()
    val alertsChat = chatId == AppAlerts.CHAT_ID
    /** Анонимный режим включают в настройках группы; в её подгруппах он тоже действует. */
    val anonChat = groupId == MessengerAnon.GROUP_ID || chats.any {
        (it.id == chatId || (groupId.isNotBlank() && it.groupId == groupId)) && it.isAnonymousChat
    }
    /** Иконка-вопрос закреплена за встроенной группой «Неудобные вопросы». */
    val anonBadge = groupId == MessengerAnon.GROUP_ID
    /** Галочка стоит по умолчанию: снимешь — сообщение уйдёт от твоего имени. */
    var anonymous by remember(chatId) { mutableStateOf(true) }

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

    /** Текст строки «ответ на…»: у голосового и удалённого сообщения своего текста нет. */
    fun draftPreview(message: MessengerMessage): String = when {
        message.isVoice -> MessengerRu.voiceMessage
        message.deleted -> MessengerRu.messageDeleted
        else -> message.body
    }

    /** Копировать нечего у голосовых и удалённых сообщений — они в текст не попадают. */
    fun copyTexts(ids: List<Long>) {
        val text = messages
            .filter { it.id in ids && !it.deleted && !it.isVoice && it.body.isNotBlank() }
            .joinToString("\n\n") { it.body }
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(MessengerRu.title, text))
    }

    fun toggleSelection(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clearSelection() {
        selecting = false
        selected = emptySet()
    }

    fun sendDraft() {
        val text = draft
        val replyId = replyTo?.id ?: 0L
        draft = ""
        replyTo = null
        viewModel.sendText(chatId, text, replyId, anonChat && anonymous)
    }

    menuFor?.let { target ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(MessengerRu.messageActions) },
            text = {
                Column {
                    if (!target.deleted) {
                        Text(
                            MessengerRu.messageReaction,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            REACTION_EMOJIS.forEach { emoji ->
                                val active = target.reactions.any { it.mine && it.emoji == emoji }
                                Text(
                                    emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            menuFor = null
                                            viewModel.toggleReaction(
                                                target.id,
                                                if (active) "" else emoji
                                            )
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = {
                            replyTo = target
                            menuFor = null
                        }) { Text(MessengerRu.messageReply, color = Forest) }
                        TextButton(onClick = {
                            forwardIds = listOf(target.id)
                            menuFor = null
                        }) { Text(MessengerRu.messageForward, color = Forest) }
                        if (!target.isVoice) {
                            TextButton(onClick = {
                                copyTexts(listOf(target.id))
                                menuFor = null
                            }) { Text(MessengerRu.messageCopy, color = Forest) }
                        }
                        TextButton(onClick = {
                            val unpin = pinned?.id == target.id
                            menuFor = null
                            if (unpin) {
                                viewModel.unpinMessage(chatId)
                            } else {
                                viewModel.pinMessage(chatId, target.id)
                            }
                        }) {
                            Text(
                                if (pinned?.id == target.id) {
                                    MessengerRu.messageUnpin
                                } else {
                                    MessengerRu.messagePin
                                },
                                color = Forest
                            )
                        }
                    }
                    TextButton(onClick = {
                        selecting = true
                        selected = setOf(target.id)
                        menuFor = null
                    }) { Text(MessengerRu.messageSelect, color = Forest) }
                    if (target.mine && !target.deleted && !target.isVoice) {
                        TextButton(onClick = {
                            editDraft = target.body
                            editFor = target
                            menuFor = null
                        }) { Text(MessengerRu.messageEdit, color = Forest) }
                    }
                    // Удалять чужие и системные сообщения разрешено администратору.
                    if (!target.deleted && (target.mine || isAdmin)) {
                        TextButton(onClick = {
                            deleteIds = listOf(target.id)
                            menuFor = null
                        }) { Text(MessengerRu.messageDelete, color = Forest) }
                    }
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
    if (deleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteIds = emptyList() },
            title = { Text(MessengerRu.messageDelete) },
            text = { Text(MessengerRu.messageDeleteQuestion) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = deleteIds
                    deleteIds = emptyList()
                    clearSelection()
                    ids.forEach { viewModel.deleteMessage(it) }
                }) { Text(MessengerRu.confirmYes, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { deleteIds = emptyList() }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }
    if (forwardIds.isNotEmpty()) {
        val ids = forwardIds
        val targets = chats.filter { it.id != AppAlerts.CHAT_ID }
        AlertDialog(
            onDismissRequest = { forwardIds = emptyList() },
            title = { Text(MessengerRu.messageForwardTitle) },
            text = {
                if (targets.isEmpty()) {
                    Text(MessengerRu.messageForwardEmpty)
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(targets, key = { it.id }) { target ->
                            TextButton(
                                onClick = {
                                    forwardIds = emptyList()
                                    clearSelection()
                                    ids.forEach { viewModel.forwardMessage(target.id, it) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    target.title.ifBlank { MessengerRu.title },
                                    color = Forest,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { forwardIds = emptyList() }) {
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
                        if (anonBadge) {
                            AnonQuestionsAvatar(34.dp)
                            Spacer(Modifier.size(10.dp))
                        } else if (avatarUrl.isNotBlank() && !alertsChat) {
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
                pinned?.takeIf { !alertsChat }?.let { pinnedItem ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(SandDeep)
                            .clickable {
                                val index = messages.indexOfFirst { it.id == pinnedItem.id }
                                if (index >= 0) {
                                    pinnedScope.launch { listState.animateScrollToItem(index) }
                                }
                            }
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                MessengerRu.messagePinnedTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = Forest
                            )
                            Text(
                                if (pinnedItem.isVoice) {
                                    MessengerRu.voiceMessage
                                } else {
                                    pinnedItem.body
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        IconButton(onClick = { viewModel.unpinMessage(chatId) }) {
                            Icon(Icons.Outlined.Close, MessengerRu.messageUnpin, tint = Forest)
                        }
                    }
                }
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
                            selected = selecting && message.id in selected,
                            onPlay = { viewModel.playVoice(message) },
                            onReaction = { emoji -> viewModel.toggleReaction(message.id, emoji) },
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
                            onTap = when {
                                selecting -> {
                                    { toggleSelection(message.id) }
                                }
                                !alertsChat && !message.deleted -> {
                                    { menuFor = message }
                                }
                                else -> null
                            },
                            onLongPress = if (!alertsChat && !message.deleted) {
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
                if (!alertsChat && selecting) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Sand.copy(alpha = 0.96f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${MessengerRu.messageSelected}: ${selected.size}",
                            modifier = Modifier.weight(1f),
                            color = Forest
                        )
                        TextButton(onClick = { forwardIds = selected.toList() }) {
                            Text(MessengerRu.messageForward, color = Forest)
                        }
                        TextButton(onClick = { copyTexts(selected.toList()) }) {
                            Text(MessengerRu.messageCopy, color = Forest)
                        }
                        TextButton(onClick = { deleteIds = selected.toList() }) {
                            Text(MessengerRu.messageDelete, color = Forest)
                        }
                        TextButton(onClick = { clearSelection() }) {
                            Text(MessengerRu.messageSelectCancel, color = Forest)
                        }
                    }
                }
                if (!alertsChat && !selecting) {
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
                replyTo?.let { target ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(SandDeep)
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${MessengerRu.messageReplyTo}: " +
                                    if (target.mine) myName else target.senderName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Forest
                            )
                            Text(
                                draftPreview(target),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { replyTo = null }) {
                            Icon(Icons.Outlined.Close, MessengerRu.messageReplyCancel, tint = Forest)
                        }
                    }
                }
                if (anonChat && !recording) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = anonymous,
                            onCheckedChange = { anonymous = it },
                            colors = CheckboxDefaults.colors(checkedColor = Forest)
                        )
                        Text(
                            MessengerRu.anonSend,
                            style = MaterialTheme.typography.labelMedium,
                            color = Forest
                        )
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
                                                val replyId = replyTo?.id ?: 0L
                                                replyTo = null
                                                viewModel.sendVoice(chatId, result.first, result.second, replyId)
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

/** Быстрые реакции: как в мессенджерах, всегда под рукой в меню сообщения. */
private val REACTION_EMOJIS = listOf("👍", "❤️", "🔥", "😂", "👏", "🙏")

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
    selected: Boolean = false,
    onReaction: (String) -> Unit = {},
    onOpen: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
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
                    if (selected) {
                        Modifier.border(
                            2.dp,
                            if (mine) Sand else Forest,
                            RoundedCornerShape(16.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    when {
                        onOpen != null -> Modifier.clickable(onClick = onOpen)
                        onTap != null -> Modifier.clickable(onClick = onTap)
                        else -> Modifier
                    }
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
            if (message.isForwarded) {
                Text(
                    "${MessengerRu.messageForwardFrom} ${message.forwardFrom}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mine) Sand.copy(alpha = 0.85f) else Forest
                )
                Spacer(Modifier.height(2.dp))
            }
            if (message.isReply && !message.deleted) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (mine) Sand.copy(alpha = 0.18f) else Forest.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        message.replySenderName.ifBlank { MessengerRu.messageReplyTo },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (mine) Sand else Forest
                    )
                    Text(
                        message.replyPreview(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mine) Sand.copy(alpha = 0.85f) else Forest.copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(4.dp))
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
            if (message.hasReactions) {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.forEach { reaction ->
                        val highlight = if (reaction.mine) {
                            if (mine) Sand.copy(alpha = 0.3f) else Forest.copy(alpha = 0.18f)
                        } else if (mine) {
                            Sand.copy(alpha = 0.12f)
                        } else {
                            Forest.copy(alpha = 0.08f)
                        }
                        Text(
                            "${reaction.emoji} ${reaction.count}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (mine) Sand else Forest,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(highlight)
                                .clickable {
                                    onReaction(if (reaction.mine) "" else reaction.emoji)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
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
