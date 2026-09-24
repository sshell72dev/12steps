package ru.na.step4.obidy.ui.messenger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
    onShowQr: () -> Unit,
    onShowTopics: () -> Unit,
    onGroupDeleted: () -> Unit = {}
) {
    val info by viewModel.groupInfo.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val refresh by viewModel.groupRefresh.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var nameDraft by remember(info?.id) { mutableStateOf(info?.name.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<MessengerContact?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadGroupAvatar(groupId, uri)
    }
    LaunchedEffect(groupId, refresh) {
        viewModel.loadGroup(groupId)
    }
    val memberIds = info?.members?.map { it.id }?.toSet().orEmpty()
    val addable = contacts.filter { it.id !in memberIds }
    val canManage = info?.canManage == true
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MessengerAvatar(
                    avatarUrl = info?.avatarUrl.orEmpty(),
                    title = info?.name.orEmpty(),
                    size = 96.dp,
                    viewModel = viewModel
                )
                if (canManage) {
                    JournalButton(
                        label = if (info?.avatarUrl.isNullOrBlank()) {
                            MessengerRu.groupPhotoAdd
                        } else {
                            MessengerRu.photoChange
                        },
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        filled = true
                    )
                    if (!info?.avatarUrl.isNullOrBlank()) {
                        JournalButton(
                            label = MessengerRu.photoDelete,
                            onClick = { viewModel.deleteGroupAvatar(groupId) }
                        )
                    }
                    Text(MessengerRu.groupSettings, style = MaterialTheme.typography.titleMedium, color = Forest)
                    VoiceOutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(MessengerRu.renameHint) },
                        singleLine = true
                    )
                    JournalButton(
                        label = MessengerRu.save,
                        onClick = {
                            val trimmed = nameDraft.trim()
                            if (trimmed.isNotBlank()) viewModel.renameGroup(groupId, trimmed)
                        },
                        filled = true
                    )
                }
                JournalButton(label = MessengerRu.groupQr, onClick = {
                    info?.let { viewModel.openGroupQr(it) }
                    onShowQr()
                }, filled = true)
                JournalButton(
                    label = MessengerRu.topicsOpen,
                    onClick = onShowTopics
                )
                Text(MessengerRu.topicsHint, style = MaterialTheme.typography.bodySmall)
                Text(MessengerRu.members, style = MaterialTheme.typography.titleMedium, color = Forest)
                info?.members?.forEach { member ->
                    MemberRow(
                        member = member,
                        ownerLabel = if (info?.ownerId == member.id) MessengerRu.owner else "",
                        canRemove = canManage && member.id != info?.ownerId,
                        viewModel = viewModel,
                        onRemove = { memberToRemove = member }
                    )
                }
                if (canManage) {
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
                    JournalButton(
                        label = MessengerRu.deleteGroup,
                        onClick = { confirmDelete = true }
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(MessengerRu.deleteGroup) },
            text = { Text(MessengerRu.deleteGroupQuestion) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteGroup(groupId) { onGroupDeleted() }
                }) { Text(MessengerRu.confirmYes, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text(MessengerRu.removeMember) },
            text = {
                Text(
                    "${MessengerRu.removeMemberQuestion} " +
                        member.displayName.ifBlank { member.id.take(8) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = member.id
                    memberToRemove = null
                    viewModel.removeMember(groupId, target)
                }) { Text(MessengerRu.confirmYes, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text(MessengerRu.confirmNo, color = Forest)
                }
            }
        )
    }
}

@Composable
private fun MemberRow(
    member: MessengerContact,
    ownerLabel: String,
    canRemove: Boolean,
    viewModel: MessengerViewModel,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MessengerAvatar(
            avatarUrl = member.avatarUrl,
            title = member.displayName.ifBlank { member.id.take(8) },
            size = 40.dp,
            viewModel = viewModel
        )
        Text(
            buildString {
                append(member.displayName.ifBlank { member.id.take(8) })
                if (ownerLabel.isNotBlank()) append(" · $ownerLabel")
            },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.PersonRemove, MessengerRu.removeMember, tint = Forest)
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
