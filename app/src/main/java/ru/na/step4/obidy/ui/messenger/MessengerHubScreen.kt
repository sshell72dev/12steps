package ru.na.step4.obidy.ui.messenger

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import ru.na.step4.obidy.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import ru.na.step4.obidy.data.messenger.MessengerChallenge
import ru.na.step4.obidy.data.messenger.MessengerChat
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerHubScreen(
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onOpenChat: (MessengerChat) -> Unit,
    onMyQr: () -> Unit,
    onScan: () -> Unit,
    onNewGroup: () -> Unit,
    onProfile: () -> Unit,
    onJoinChallenge: (String) -> Unit
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val challenges by viewModel.challenges.collectAsStateWithLifecycle()
    val openChallenges = challenges.filterNot { it.joined }
    DisposableEffect(Unit) {
        viewModel.startHubPolling()
        onDispose { viewModel.stopHubPolling() }
    }
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(MessengerRu.title, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Outlined.AccountCircle, MessengerRu.profileTitle, tint = Forest)
                    }
                    IconButton(onClick = onMyQr) {
                        Icon(Icons.Outlined.QrCode, MessengerRu.myQr, tint = Forest)
                    }
                    IconButton(onClick = onNewGroup) {
                        Icon(Icons.Outlined.GroupAdd, MessengerRu.newGroup, tint = Forest)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScan, containerColor = Forest, contentColor = Sand) {
                Icon(Icons.Outlined.QrCodeScanner, MessengerRu.scanQr)
            }
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            if (chats.isEmpty() && openChallenges.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        MessengerRu.emptyChats,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (openChallenges.isNotEmpty()) {
                        item {
                            Text(
                                MessengerRu.challenges,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = Forest
                            )
                        }
                        items(openChallenges, key = { "challenge-${it.key}" }) { item ->
                            ChallengeJoinCard(
                                challenge = item,
                                onJoin = { onJoinChallenge(item.key) }
                            )
                        }
                    }
                    items(chats, key = { it.id }) { chat ->
                        ChatRow(chat = chat, viewModel = viewModel, onClick = { onOpenChat(chat) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ChallengeJoinCard(
    challenge: MessengerChallenge,
    onJoin: () -> Unit
) {
    val title = MessengerRu.challengeTitle(challenge.key, challenge.name)
    val body = MessengerRu.challengeBody(challenge.key)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Forest)
        if (body.isNotBlank()) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (challenge.members > 0) {
            Text(
                "${challenge.members} ${MessengerRu.challengeMembers}",
                style = MaterialTheme.typography.labelMedium,
                color = Amber
            )
        }
        JournalButton(MessengerRu.challengeJoin, onJoin, filled = true)
    }
}

@Composable
private fun ChatRow(chat: MessengerChat, viewModel: MessengerViewModel, onClick: () -> Unit) {
    val preview = if (chat.lastKind == "voice" && chat.lastBody.isBlank()) {
        MessengerRu.voiceMessage
    } else {
        chat.lastBody
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chat.isService) {
            Image(
                painter = painterResource(R.drawable.ic_app_avatar),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        } else {
            MessengerAvatar(
                avatarUrl = chat.avatarUrl,
                title = chat.title,
                size = 48.dp,
                viewModel = viewModel
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                chat.title.ifBlank { MessengerRu.title },
                style = MaterialTheme.typography.titleMedium,
                color = Forest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (chat.lastAt > 0) {
                Text(
                    formatChatTime(chat.lastAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (chat.unread > 0) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Amber)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (chat.unread > 99) "99+" else chat.unread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Forest
                    )
                }
            }
        }
    }
}

internal fun formatChatTime(ms: Long): String {
    if (ms <= 0) return ""
    val cal = Calendar.getInstance()
    val nowDay = cal.get(Calendar.DAY_OF_YEAR)
    val nowYear = cal.get(Calendar.YEAR)
    cal.timeInMillis = ms
    return when {
        cal.get(Calendar.YEAR) == nowYear && cal.get(Calendar.DAY_OF_YEAR) == nowDay ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(ms))
    }
}

/**
 * Время сообщения внутри ленты: дата уже стоит в разделителе дня, поэтому
 * у сообщения всегда показываем часы и минуты — иначе у всех сообщений
 * одного дня подряд стояла бы одна и та же дата.
 */
internal fun formatMessageTime(ms: Long): String {
    if (ms <= 0) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}

internal fun formatDayLabel(ms: Long): String {
    if (ms <= 0) return ""
    val cal = Calendar.getInstance()
    val nowDay = cal.get(Calendar.DAY_OF_YEAR)
    val nowYear = cal.get(Calendar.YEAR)
    cal.timeInMillis = ms
    return when {
        cal.get(Calendar.YEAR) == nowYear && cal.get(Calendar.DAY_OF_YEAR) == nowDay -> MessengerRu.today
        cal.get(Calendar.YEAR) == nowYear && cal.get(Calendar.DAY_OF_YEAR) == nowDay - 1 -> MessengerRu.yesterday
        else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(ms))
    }
}

internal fun sameDay(a: Long, b: Long): Boolean {
    if (a <= 0 || b <= 0) return false
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}