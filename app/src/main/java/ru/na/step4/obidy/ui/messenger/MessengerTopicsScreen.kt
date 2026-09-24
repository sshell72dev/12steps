package ru.na.step4.obidy.ui.messenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.data.messenger.MessengerTopic
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

/**
 * Подгруппы группы — как темы в Telegram: каждая со своей лентой,
 * «Общий» — общая лента всей группы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerTopicsScreen(
    groupId: String,
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenTopic: (String, String) -> Unit
) {
    val info by viewModel.groupInfo.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val refresh by viewModel.groupRefresh.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var createDraft by remember { mutableStateOf("") }
    var renameDraft by remember { mutableStateOf("") }
    var topicToRename by remember { mutableStateOf<MessengerTopic?>(null) }
    var topicToDelete by remember { mutableStateOf<MessengerTopic?>(null) }
    val canManage = info?.canManage == true

    LaunchedEffect(groupId, refresh) {
        viewModel.loadGroup(groupId)
        viewModel.loadTopics(groupId)
    }

    fun openTopic(topic: MessengerTopic) {
        if (topic.chatId.isBlank()) return
        val groupName = info?.name.orEmpty()
        val chatTitle = if (topic.isGeneral) {
            groupName.ifBlank { MessengerRu.topicGeneral }
        } else {
            listOf(groupName, topic.name).filter { it.isNotBlank() }.joinToString(" · ")
        }
        onOpenTopic(topic.chatId, chatTitle)
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MessengerAvatar(
                            avatarUrl = info?.avatarUrl.orEmpty(),
                            title = info?.name.orEmpty(),
                            size = 32.dp,
                            viewModel = viewModel
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                info?.name.orEmpty().ifBlank { MessengerRu.title },
                                color = Forest,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                MessengerRu.topicsTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    IconButton(onClick = { onOpenGroup(groupId) }) {
                        Icon(Icons.Outlined.Info, MessengerRu.groupInfo, tint = Forest)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(
                    onClick = {
                        createDraft = ""
                        creating = true
                    },
                    containerColor = Forest,
                    contentColor = Sand
                ) {
                    Icon(Icons.Outlined.Add, MessengerRu.topicCreate)
                }
            }
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            if (topics.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        MessengerRu.topicsEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            MessengerRu.topicsScreenHint,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(topics, key = { it.id.ifBlank { "general" } }) { topic ->
                        TopicRow(
                            topic = topic,
                            title = if (topic.isGeneral) MessengerRu.topicGeneral else topic.name,
                            canManage = canManage && !topic.isGeneral,
                            onOpen = { openTopic(topic) },
                            onRename = {
                                renameDraft = topic.name
                                topicToRename = topic
                            },
                            onDelete = { topicToDelete = topic }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(MessengerRu.topicCreate) },
            text = {
                VoiceOutlinedTextField(
                    value = createDraft,
                    onValueChange = { createDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(MessengerRu.topicNameHint) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = createDraft.trim()
                    creating = false
                    if (trimmed.isNotBlank()) viewModel.createTopic(groupId, trimmed)
                }) { Text(MessengerRu.save, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }

    topicToRename?.let { topic ->
        AlertDialog(
            onDismissRequest = { topicToRename = null },
            title = { Text(MessengerRu.topicRename) },
            text = {
                VoiceOutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(MessengerRu.topicNameHint) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameDraft.trim()
                    if (trimmed.isNotBlank()) viewModel.renameTopic(groupId, topic.id, trimmed)
                    topicToRename = null
                }) { Text(MessengerRu.save, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { topicToRename = null }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }

    topicToDelete?.let { topic ->
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text(MessengerRu.topicDelete) },
            text = { Text(MessengerRu.topicDeleteQuestion) },
            confirmButton = {
                TextButton(onClick = {
                    val target = topic.id
                    topicToDelete = null
                    viewModel.deleteTopic(groupId, target)
                }) { Text(MessengerRu.confirmYes, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }
}

@Composable
private fun TopicRow(
    topic: MessengerTopic,
    title: String,
    canManage: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Forest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (topic.lastBody.isNotBlank()) {
                Text(
                    topic.lastBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (topic.lastAt > 0) {
            Text(
                formatChatTime(topic.lastAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (topic.unread > 0) {
            Text(
                " ${topic.unread}",
                style = MaterialTheme.typography.labelLarge,
                color = Forest
            )
        }
        if (canManage) {
            IconButton(onClick = onRename) {
                Icon(Icons.Outlined.Edit, MessengerRu.topicRename, tint = Forest)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, MessengerRu.topicDelete, tint = Forest)
            }
        }
    }
}
