package ru.na.step4.obidy.ui.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.support.SupportClient
import ru.na.step4.obidy.data.support.SupportMessage
import ru.na.step4.obidy.data.support.SupportRepository
import ru.na.step4.obidy.data.support.SupportRu
import ru.na.step4.obidy.data.support.SupportStatus
import ru.na.step4.obidy.data.support.SupportTicket
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@Composable
fun SupportInboxScreen(repository: SupportRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var opened by remember { mutableStateOf<SupportTicket?>(null) }
    var reply by remember { mutableStateOf("") }
    var taskDone by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingDeleteMessage by remember { mutableStateOf<SupportMessage?>(null) }
    var pendingDeleteTicket by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<SupportMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }

    fun reload() {
        scope.launch { tickets = repository.inbox() }
    }

    fun applyTicket(ticket: SupportTicket) {
        opened = ticket
        taskDone = ticket.status == SupportStatus.DONE
        tickets = tickets.map { if (it.id == ticket.id) ticket else it }
    }

    fun markMessageReadIfNeeded(msg: SupportMessage) {
        if (!msg.fromUser || msg.adminRead || busy) return
        busy = true
        scope.launch {
            val fresh = repository.markMessageRead(msg.id)
            if (fresh != null) applyTicket(fresh)
            busy = false
            reload()
        }
    }

    LaunchedEffect(Unit) { reload() }

    val current = opened
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(SupportRu.inbox, color = Forest, style = MaterialTheme.typography.titleLarge)
        if (!notice.isNullOrBlank()) {
            Text(notice.orEmpty(), color = Amber)
        }
        if (current == null) {
            if (tickets.isEmpty()) {
                Text(SupportRu.empty, color = Forest)
            } else {
                tickets.forEach { ticket ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Sand.copy(alpha = 0.7f))
                            .clickable {
                                scope.launch {
                                    val fresh = repository.open(ticket.id) ?: ticket
                                    applyTicket(fresh)
                                    reply = ""
                                    notice = null
                                }
                            }
                            .padding(10.dp)
                    ) {
                        val unread = if (!ticket.adminRead) " · ${SupportRu.unread}" else ""
                        Text(
                            "${ticket.userName.ifBlank { SupportRu.anonymous }} · ${ticket.screen}$unread",
                            color = Forest,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${SupportRu.kind}: ${ticket.kindLabel}",
                            color = Amber,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "${SupportRu.status}: ${ticket.statusLabel}",
                            color = Amber,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            SupportClient.formatWhen(ticket.createdAt),
                            color = Amber,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            ticket.preview,
                            color = Forest,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3
                        )
                    }
                }
            }
        } else {
            JournalButton("← ${SupportRu.backToList}", {
                opened = null
                reload()
            })
            TicketPreview(
                ticket = current,
                showUser = true,
                adminActions = true,
                compact = true,
                onCopyMessage = { msg ->
                    copyPlain(
                        context,
                        buildString {
                            appendLine("${SupportRu.belonging}: ${current.belongingLabel}")
                            appendLine("${SupportRu.screen}: ${current.screen}")
                            appendLine("Маршрут: ${current.screenRoute}")
                            appendLine()
                            append(msg.body)
                        }
                    )
                    markMessageReadIfNeeded(msg)
                },
                onShareMessage = { msg ->
                    sharePlain(context, msg.body)
                    markMessageReadIfNeeded(msg)
                },
                onMarkMessageRead = { msg -> markMessageReadIfNeeded(msg) },
                onEditMessage = { msg ->
                    editingMessage = msg
                    editDraft = msg.body
                },
                onDeleteMessage = { msg -> pendingDeleteMessage = msg },
                onCopyTicket = { copyPlain(context, SupportClient.shareText(current)) },
                onShareTicket = { sharePlain(context, SupportClient.shareText(current)) },
                onDeleteTicket = { pendingDeleteTicket = true }
            )
            Text(SupportRu.status, color = Forest, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SupportStatus.all.forEach { status ->
                    FilterChip(
                        selected = current.status == status,
                        onClick = {
                            if (busy || current.status == status) return@FilterChip
                            busy = true
                            scope.launch {
                                val fresh = repository.setStatus(current.id, status) ?: current
                                applyTicket(fresh)
                                busy = false
                                notice = SupportRu.statusChanged
                                reload()
                            }
                        },
                        label = { Text(SupportStatus.label(status)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Forest,
                            selectedLabelColor = Sand,
                            containerColor = SandDeep,
                            labelColor = Forest
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = taskDone,
                    onCheckedChange = { checked ->
                        if (!checked || busy) {
                            if (!checked) taskDone = false
                            return@Switch
                        }
                        busy = true
                        scope.launch {
                            val fresh = repository.complete(current.id) ?: current
                            applyTicket(fresh)
                            busy = false
                            notice = SupportRu.statusChanged
                            reload()
                        }
                    },
                    enabled = !busy,
                    colors = SwitchDefaults.colors(checkedTrackColor = Forest)
                )
                Text(SupportRu.taskDone, color = Forest, style = MaterialTheme.typography.bodyMedium)
            }
            VoiceOutlinedTextField(
                value = reply,
                onValueChange = { reply = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(SupportRu.reply) },
                placeholder = { Text(SupportRu.replyHint) },
                minLines = 3,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp)
            )
            JournalButton(SupportRu.replySend, {
                if (reply.isBlank()) return@JournalButton
                busy = true
                scope.launch {
                    val fresh = repository.reply(current.id, reply, complete = taskDone) ?: current
                    applyTicket(fresh)
                    reply = ""
                    busy = false
                    reload()
                }
            }, filled = true)
        }
    }

    pendingDeleteMessage?.let { msg ->
        ConfirmDialog(
            title = SupportRu.deleteMessage,
            onDismiss = { pendingDeleteMessage = null },
            onConfirm = {
                val id = msg.id
                pendingDeleteMessage = null
                scope.launch {
                    val fresh = repository.deleteMessage(id)
                    if (fresh != null) applyTicket(fresh) else opened = opened
                    reload()
                }
            }
        )
    }
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text(SupportRu.edit, color = Forest) },
            text = {
                VoiceOutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(SupportRu.body) },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editDraft.isNotBlank(),
                    onClick = {
                        val id = msg.id
                        val body = editDraft.trim()
                        editingMessage = null
                        scope.launch {
                            val fresh = repository.editMessage(id, body)
                            if (fresh != null) applyTicket(fresh)
                            reload()
                        }
                    }
                ) { Text(SupportRu.save) }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text(Ru.cancel) }
            }
        )
    }
    if (pendingDeleteTicket && current != null) {
        ConfirmDialog(
            title = SupportRu.deleteTicket,
            onDismiss = { pendingDeleteTicket = false },
            onConfirm = {
                pendingDeleteTicket = false
                val id = current.id
                scope.launch {
                    if (repository.deleteTicket(id)) {
                        opened = null
                        reload()
                    }
                }
            }
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Forest) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(SupportRu.delete) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Ru.cancel) }
        }
    )
}

@Composable
fun TicketPreview(
    ticket: SupportTicket,
    showUser: Boolean,
    adminActions: Boolean = false,
    compact: Boolean = false,
    onCopyMessage: ((SupportMessage) -> Unit)? = null,
    onShareMessage: ((SupportMessage) -> Unit)? = null,
    onMarkMessageRead: ((SupportMessage) -> Unit)? = null,
    onEditMessage: ((SupportMessage) -> Unit)? = null,
    onDeleteMessage: ((SupportMessage) -> Unit)? = null,
    onCopyTicket: (() -> Unit)? = null,
    onShareTicket: (() -> Unit)? = null,
    onDeleteTicket: (() -> Unit)? = null
) {
    val gap = if (compact) 4.dp else 6.dp
    val actionStyle = if (compact) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Sand.copy(alpha = 0.7f))
            .padding(if (compact) 8.dp else 10.dp)
    ) {
        val who = ticket.userName.ifBlank { SupportRu.anonymous }
        Text(
            if (showUser) "$who · ${ticket.screen}" else ticket.screen,
            style = MaterialTheme.typography.titleSmall,
            color = Forest
        )
        if (!adminActions) {
            Text(
                "${SupportRu.status}: ${ticket.statusLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = Amber
            )
        }
        Text(
            "${SupportRu.kind}: ${ticket.kindLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = Amber
        )
        Text(
            "${SupportRu.belonging}: ${ticket.belongingLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = Amber
        )
        if (ticket.adminSourceLabel.isNotBlank()) {
            Text(
                "${SupportRu.adminSource}: ${ticket.adminSourceLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = Amber
            )
        }
        Text(
            SupportClient.formatWhen(ticket.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = Amber
        )
        if (onCopyTicket != null || onShareTicket != null || onDeleteTicket != null) {
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCopyTicket != null) {
                    Text(
                        SupportRu.copy,
                        color = Forest,
                        style = actionStyle,
                        modifier = Modifier.clickable(onClick = onCopyTicket)
                    )
                }
                if (onShareTicket != null) {
                    Text(
                        SupportRu.share,
                        color = Forest,
                        style = actionStyle,
                        modifier = Modifier.clickable(onClick = onShareTicket)
                    )
                }
                if (onDeleteTicket != null) {
                    Text(
                        SupportRu.deleteTicketBtn,
                        color = Forest,
                        style = actionStyle,
                        modifier = Modifier.clickable(onClick = onDeleteTicket)
                    )
                }
            }
        }
        if (ticket.messages.isNotEmpty()) {
            ticket.messages.forEach { msg ->
                Spacer(Modifier.height(gap))
                val whoLabel = when {
                    msg.fromSystem -> SupportRu.system
                    msg.fromAdmin -> SupportRu.admin
                    else -> SupportRu.you
                }
                val readLabel = when {
                    !adminActions || !msg.fromUser -> ""
                    msg.adminRead -> " · ${SupportRu.markRead}"
                    else -> " · ${SupportRu.unreadMessage}"
                }
                val editedLabel = if (msg.edited) " · ${SupportRu.edited}" else ""
                Text(
                    "$whoLabel$readLabel$editedLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber
                )
                Text(msg.body, style = MaterialTheme.typography.bodyMedium, color = Forest)
                if (adminActions || onCopyMessage != null || onShareMessage != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onCopyMessage != null) {
                            Text(
                                SupportRu.copy,
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { onCopyMessage(msg) }
                            )
                        }
                        if (onShareMessage != null) {
                            Text(
                                SupportRu.share,
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { onShareMessage(msg) }
                            )
                        }
                        if (adminActions && onMarkMessageRead != null && msg.fromUser && !msg.adminRead) {
                            Text(
                                SupportRu.markRead,
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { onMarkMessageRead(msg) }
                            )
                        }
                        if (adminActions && onEditMessage != null) {
                            Text(
                                SupportRu.edit,
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { onEditMessage(msg) }
                            )
                        }
                        if (adminActions && onDeleteMessage != null) {
                            Text(
                                SupportRu.delete,
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { onDeleteMessage(msg) }
                            )
                        }
                    }
                }
            }
        } else if (ticket.preview.isNotBlank()) {
            Text(ticket.preview, style = MaterialTheme.typography.bodyMedium, color = Forest)
        }
    }
}

fun copyPlain(context: Context, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(SupportRu.inbox, text))
    Toast.makeText(context, SupportRu.copied, Toast.LENGTH_SHORT).show()
}

fun sharePlain(context: Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, SupportRu.report)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, SupportRu.share))
}
