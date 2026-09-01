package ru.na.step4.obidy.ui.messenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import ru.na.step4.obidy.data.messenger.MessengerContact
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerGroupCreateScreen(
    contacts: StateFlow<List<MessengerContact>>,
    onBack: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    val friends by contacts.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(MessengerRu.newGroup, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VoiceOutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(MessengerRu.groupName) },
                    placeholder = { Text(MessengerRu.groupNameHint) },
                    singleLine = true
                )
                Text(MessengerRu.addFriends, style = MaterialTheme.typography.titleMedium, color = Forest)
                if (friends.isEmpty()) {
                    Text(MessengerRu.noFriends, style = MaterialTheme.typography.bodyMedium)
                } else {
                    friends.forEach { friend ->
                        FriendCheckRow(
                            name = friend.displayName.ifBlank { friend.id.take(8) },
                            checked = friend.id in selected,
                            onToggle = {
                                selected = if (friend.id in selected) selected - friend.id else selected + friend.id
                            }
                        )
                    }
                }
                JournalButton(
                    label = MessengerRu.createGroup,
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotBlank()) onCreate(trimmed, selected.toList())
                    },
                    filled = true
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerGroupInfoScreen(
    groupId: String,
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onShowQr: () -> Unit
) {
    val info by viewModel.groupInfo.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }
    val memberIds = info?.members?.map { it.id }?.toSet().orEmpty()
    val addable = contacts.filter { it.id !in memberIds }
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(info?.name ?: MessengerRu.newGroup, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                JournalButton(label = MessengerRu.groupQr, onClick = {
                    info?.let { viewModel.openGroupQr(it) }
                    onShowQr()
                }, filled = true)
                Text(MessengerRu.members, style = MaterialTheme.typography.titleMedium, color = Forest)
                info?.members?.forEach { member ->
                    Text(
                        buildString {
                            append(member.displayName.ifBlank { member.id.take(8) })
                            if (info?.ownerId == member.id) append(" · ${MessengerRu.owner}")
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (info?.isOwner == true) {
                    Text(MessengerRu.addToGroup, style = MaterialTheme.typography.titleMedium, color = Forest)
                    if (addable.isEmpty()) {
                        Text(MessengerRu.noFriends, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        addable.forEach { friend ->
                            FriendCheckRow(
                                name = friend.displayName.ifBlank { friend.id.take(8) },
                                checked = friend.id in selected,
                                onToggle = {
                                    selected = if (friend.id in selected) {
                                        selected - friend.id
                                    } else {
                                        selected + friend.id
                                    }
                                }
                            )
                        }
                        JournalButton(
                            label = MessengerRu.addToGroup,
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    viewModel.addMembers(groupId, selected.toList())
                                    selected = emptySet()
                                }
                            },
                            filled = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendCheckRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}
