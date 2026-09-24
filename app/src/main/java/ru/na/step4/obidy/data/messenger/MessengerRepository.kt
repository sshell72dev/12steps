package ru.na.step4.obidy.data.messenger

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.alerts.AppAlerts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.data.profile.ProfileStore

class MessengerRepository(
    context: Context,
    private val profileStore: ProfileStore
) {
    private val appContext = context.applicationContext
    private val prefs = MessengerPrefs(appContext)
    private val dao = MessengerDatabase.get(appContext).dao()
    private val client = MessengerClient { prefs.messengerId }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val voicePlayer = MessengerVoicePlayer()
    val voiceRecorder = MessengerVoiceRecorder(appContext)

    private val _enabled = MutableStateFlow(prefs.enabledCached)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _myAvatarUrl = MutableStateFlow("")
    val myAvatarUrl: StateFlow<String> = _myAvatarUrl.asStateFlow()

    private val avatarMemory = ConcurrentHashMap<String, ByteArray>()

    private val _groupRefresh = MutableStateFlow(0)

    /** Меняется после правок группы и фото, чтобы экран группы перечитал данные. */
    val groupRefresh: StateFlow<Int> = _groupRefresh.asStateFlow()

    /** Идентификатор профиля в мессенджере: по нему сервер отличает владельца группы. */
    val myId: String get() = prefs.messengerId

    private val _pairToken = MutableStateFlow("")
    val pairToken: StateFlow<String> = _pairToken.asStateFlow()

    private val _pendingInvite = MutableStateFlow<String?>(null)
    val pendingInvite: StateFlow<String?> = _pendingInvite.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val chats: Flow<List<MessengerChat>> = dao.observeChats().map { rows ->
        val mapped = rows.map { it.toChat() }
        mapped.sortedWith(
            compareByDescending<MessengerChat> { it.id == AppAlerts.CHAT_ID }
                .thenByDescending { it.lastAt }
        )
    }

    val contacts: Flow<List<MessengerContact>> = dao.observeContacts().map { rows ->
        rows.map { MessengerContact(it.id, it.displayName) }
    }

    fun messages(chatId: String): Flow<List<MessengerMessage>> {
        return dao.observeMessages(chatId).map { rows -> rows.map { it.toMessage() } }
    }

    fun resolvedName(): String {
        val stored = prefs.displayName.trim()
        if (stored.isNotBlank()) return stored
        return profileStore.name.trim()
    }

    fun needsNickname(): Boolean = resolvedName().isBlank()

    fun offerInvite(raw: String?) {
        val token = raw?.let { MessengerInvite.parse(it) } ?: return
        _pendingInvite.value = token
    }

    fun consumeInvite(): String? {
        val token = _pendingInvite.value
        _pendingInvite.value = null
        return token
    }

    fun clearError() {
        _error.value = null
    }

    suspend fun refreshEnabled(): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.statusEnabled()) {
            is MessengerResult.Ok -> applyEnabled(result.value)
            is MessengerResult.Disabled -> applyEnabled(false)
            is MessengerResult.Err -> _enabled.value
        }
    }

    suspend fun ensureRegistered(): Boolean = withContext(Dispatchers.IO) {
        if (!refreshEnabled()) return@withContext false
        val name = resolvedName()
        if (name.isBlank()) return@withContext false
        when (val result = client.register(name)) {
            is MessengerResult.Ok -> {
                prefs.displayName = result.value.first.displayName.ifBlank { name }
                _displayName.value = prefs.displayName
                _myAvatarUrl.value = result.value.first.avatarUrl
                _pairToken.value = result.value.second
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun saveNickname(name: String): Boolean {
        prefs.displayName = name
        _displayName.value = prefs.displayName
        return ensureRegistered()
    }

    suspend fun refreshChats() = withContext(Dispatchers.IO) {
        when (val result = client.chats()) {
            is MessengerResult.Ok -> dao.upsertChats(result.value.map { it.toRow() })
            is MessengerResult.Disabled -> applyEnabled(false)
            is MessengerResult.Err -> _error.value = result.message.ifBlank { MessengerRu.error }
        }
        ensureAlertsChat()
    }

    suspend fun refreshContacts() = withContext(Dispatchers.IO) {
        when (val result = client.contacts()) {
            is MessengerResult.Ok -> {
                dao.clearContacts()
                dao.upsertContacts(result.value.map { MessengerContactRow(it.id, it.displayName) })
            }
            is MessengerResult.Disabled -> applyEnabled(false)
            is MessengerResult.Err -> Unit
        }
    }

    suspend fun refreshChallenges(): List<MessengerChallenge> = withContext(Dispatchers.IO) {
        when (val result = client.challenges()) {
            is MessengerResult.Ok -> {
                prefs.putChallenges(result.value)
                result.value
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                emptyList()
            }
            is MessengerResult.Err -> emptyList()
        }
    }

    suspend fun joinChallenge(key: String): MessengerJoinResult? = withContext(Dispatchers.IO) {
        when (val result = client.joinChallenge(key)) {
            is MessengerResult.Ok -> {
                if (result.value.chatId.isNotBlank()) {
                    prefs.putChallengeChat(key, result.value.chatId)
                }
                refreshChats()
                refreshChallenges()
                result.value
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                null
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                null
            }
        }
    }

    suspend fun shareChallenge(key: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (!enabled.value || body.isBlank()) return@withContext false
        val chatId = prefs.challengeChatId(key)
        if (chatId.isBlank()) return@withContext false
        sendText(chatId, body)
    }

    suspend fun refreshMessages(chatId: String) = withContext(Dispatchers.IO) {
        if (chatId == AppAlerts.CHAT_ID) {
            markAlertsRead()
            return@withContext
        }
        when (val result = client.messages(chatId, 0)) {
            is MessengerResult.Ok -> {
                // Полная сверка ленты: так приходят и новые сообщения,
                // и правки, и удаления.
                dao.replaceMessages(chatId, result.value.map { it.toRow() })
                if (result.value.isNotEmpty()) {
                    client.markRead(chatId, result.value.maxOf { it.id })
                }
            }
            is MessengerResult.Disabled -> applyEnabled(false)
            is MessengerResult.Err -> _error.value = result.message.ifBlank { MessengerRu.error }
        }
    }

    suspend fun sendText(chatId: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (chatId == AppAlerts.CHAT_ID) return@withContext false
        when (val result = client.sendText(chatId, body)) {
            is MessengerResult.Ok -> {
                dao.upsertMessages(listOf(result.value.toRow()))
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun sendVoice(chatId: String, file: File, durationMs: Int): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.sendVoice(chatId, file, durationMs)) {
            is MessengerResult.Ok -> {
                dao.upsertMessages(listOf(result.value.toRow()))
                file.delete()
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun editMessage(messageId: Long, body: String): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.editMessage(messageId, body)) {
            is MessengerResult.Ok -> {
                dao.upsertMessages(listOf(result.value.toRow()))
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.deleteMessage(messageId)) {
            is MessengerResult.Ok -> {
                dao.upsertMessages(listOf(result.value.toRow()))
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun rotatePairToken(): String = withContext(Dispatchers.IO) {
        when (val result = client.rotatePair()) {
            is MessengerResult.Ok -> {
                _pairToken.value = result.value
                result.value
            }
            else -> _pairToken.value
        }
    }

    suspend fun join(token: String): MessengerResult<MessengerJoinResult> = withContext(Dispatchers.IO) {
        when (val result = client.join(token)) {
            is MessengerResult.Ok -> {
                refreshChats()
                refreshContacts()
                result
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                result
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                result
            }
        }
    }

    suspend fun createGroup(name: String, userIds: List<String>): MessengerJoinResult? = withContext(Dispatchers.IO) {
        when (val result = client.createGroup(name, userIds)) {
            is MessengerResult.Ok -> {
                refreshChats()
                result.value
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                null
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                null
            }
        }
    }

    suspend fun loadGroup(groupId: String): MessengerGroupInfo? = withContext(Dispatchers.IO) {
        when (val result = client.group(groupId)) {
            is MessengerResult.Ok -> result.value
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                null
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                null
            }
        }
    }

    suspend fun addMembers(groupId: String, userIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.addMembers(groupId, userIds)) {
            is MessengerResult.Ok -> {
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun refreshProfile(): MessengerUser? = withContext(Dispatchers.IO) {
        when (val result = client.me()) {
            is MessengerResult.Ok -> {
                _myAvatarUrl.value = result.value.first.avatarUrl
                _pairToken.value = result.value.second
                result.value.first
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                null
            }
            is MessengerResult.Err -> null
        }
    }

    suspend fun avatarBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val key = url.trim()
        if (key.isBlank()) return@withContext null
        avatarMemory[key]?.let { return@withContext it }
        val cached = avatarCacheFile(key)
        if (cached.isFile && cached.length() > 0) {
            val bytes = runCatching { cached.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                avatarMemory[key] = bytes
                return@withContext bytes
            }
        }
        when (val result = client.imageBytes(key)) {
            is MessengerResult.Ok -> {
                avatarMemory[key] = result.value
                runCatching { cached.writeBytes(result.value) }
                result.value
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                null
            }
            is MessengerResult.Err -> null
        }
    }

    private fun avatarCacheFile(url: String): File {
        val dir = File(appContext.cacheDir, "messenger_avatars").apply { mkdirs() }
        return File(dir, "a_${Integer.toHexString(url.hashCode())}.img")
    }

    suspend fun uploadMyAvatar(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val file = MessengerImage.prepare(appContext, uri)
        if (file == null) {
            _error.value = MessengerRu.photoBadFormat
            return@withContext false
        }
        val result = client.uploadMyAvatar(file, MessengerImage.mimeType)
        file.delete()
        when (result) {
            is MessengerResult.Ok -> {
                _myAvatarUrl.value = result.value.avatarUrl
                refreshChats()
                refreshContacts()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun deleteMyAvatar(): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.deleteMyAvatar()) {
            is MessengerResult.Ok -> {
                _myAvatarUrl.value = ""
                refreshChats()
                refreshContacts()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun renameGroup(groupId: String, name: String): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.renameGroup(groupId, name)) {
            is MessengerResult.Ok -> {
                _groupRefresh.value = _groupRefresh.value + 1
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun deleteGroup(groupId: String): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.deleteGroup(groupId)) {
            is MessengerResult.Ok -> {
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun removeMember(groupId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.removeMember(groupId, userId)) {
            is MessengerResult.Ok -> {
                _groupRefresh.value = _groupRefresh.value + 1
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun loadTopics(groupId: String): List<MessengerTopic> = withContext(Dispatchers.IO) {
        when (val result = client.topics(groupId)) {
            is MessengerResult.Ok -> result.value
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                emptyList()
            }
            is MessengerResult.Err -> emptyList()
        }
    }

    suspend fun createTopic(groupId: String, name: String): MessengerTopic? = withContext(Dispatchers.IO) {
        when (val result = client.createTopic(groupId, name)) {
            is MessengerResult.Ok -> {
                _groupRefresh.value = _groupRefresh.value + 1
                result.value
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                null
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                null
            }
        }
    }

    suspend fun renameTopic(groupId: String, topicId: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            when (val result = client.renameTopic(groupId, topicId, name)) {
                is MessengerResult.Ok -> {
                    _groupRefresh.value = _groupRefresh.value + 1
                    true
                }
                is MessengerResult.Disabled -> {
                    applyEnabled(false)
                    false
                }
                is MessengerResult.Err -> {
                    _error.value = result.message.ifBlank { MessengerRu.error }
                    false
                }
            }
        }

    suspend fun deleteTopic(groupId: String, topicId: String): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.deleteTopic(groupId, topicId)) {
            is MessengerResult.Ok -> {
                _groupRefresh.value = _groupRefresh.value + 1
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun uploadGroupAvatar(groupId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val file = MessengerImage.prepare(appContext, uri)
        if (file == null) {
            _error.value = MessengerRu.photoBadFormat
            return@withContext false
        }
        val result = client.uploadGroupAvatar(groupId, file, MessengerImage.mimeType)
        file.delete()
        when (result) {
            is MessengerResult.Ok -> {
                _groupRefresh.value = _groupRefresh.value + 1
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun deleteGroupAvatar(groupId: String): Boolean = withContext(Dispatchers.IO) {
        when (val result = client.deleteGroupAvatar(groupId)) {
            is MessengerResult.Ok -> {
                _groupRefresh.value = _groupRefresh.value + 1
                refreshChats()
                true
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                false
            }
            is MessengerResult.Err -> {
                _error.value = result.message.ifBlank { MessengerRu.error }
                false
            }
        }
    }

    suspend fun rotateGroupToken(groupId: String): String? = withContext(Dispatchers.IO) {
        when (val result = client.groupInvite(groupId, rotate = true)) {
            is MessengerResult.Ok -> result.value
            else -> null
        }
    }

    suspend fun voiceFile(message: MessengerMessage): File? = withContext(Dispatchers.IO) {
        val dir = File(appContext.cacheDir, "messenger_voice").apply { mkdirs() }
        val file = File(dir, "${message.id}.m4a")
        if (file.exists() && file.length() > 0) return@withContext file
        when (val result = client.downloadVoice(message.id)) {
            is MessengerResult.Ok -> {
                file.writeBytes(result.value)
                file
            }
            is MessengerResult.Disabled -> {
                applyEnabled(false)
                null
            }
            is MessengerResult.Err -> null
        }
    }

    fun postAlert(body: String, target: String = "") {
        val text = body.trim()
        if (text.isBlank()) return
        ioScope.launch { appendAlert(text, target) }
    }

    suspend fun postAlertNow(body: String, target: String = "") {
        val text = body.trim()
        if (text.isBlank()) return
        appendAlert(text, target)
    }

    suspend fun ensureAlertsChat() = withContext(Dispatchers.IO) {
        val existing = dao.chatById(AppAlerts.CHAT_ID)
        val title = MessengerRu.alertsTitle
        if (existing == null) {
            dao.upsertChats(
                listOf(
                    MessengerChatRow(
                        id = AppAlerts.CHAT_ID,
                        kind = AppAlerts.KIND,
                        title = title,
                        peerId = "",
                        groupId = "",
                        avatarUrl = "",
                        isOwner = false,
                        lastBody = "",
                        lastKind = "text",
                        lastAt = 0L,
                        unread = 0
                    )
                )
            )
        } else if (existing.title != title) {
            dao.upsertChats(listOf(existing.copy(title = title)))
        }
    }

    suspend fun markAlertsRead() = withContext(Dispatchers.IO) {
        val existing = dao.chatById(AppAlerts.CHAT_ID) ?: return@withContext
        if (existing.unread != 0) {
            dao.upsertChats(listOf(existing.copy(unread = 0)))
        }
    }

    private suspend fun appendAlert(text: String, target: String) = withContext(Dispatchers.IO) {
        ensureAlertsChat()
        val now = System.currentTimeMillis()
        val existing = dao.chatById(AppAlerts.CHAT_ID) ?: return@withContext
        dao.upsertMessages(
            listOf(
                MessengerMessageRow(
                    id = -now,
                    chatId = AppAlerts.CHAT_ID,
                    senderId = AppAlerts.senderIdFor(target),
                    senderName = AppAlerts.senderNameFor(target),
                    kind = "text",
                    body = text,
                    voiceDurationMs = 0,
                    createdAt = now,
                    mine = false
                )
            )
        )
        dao.upsertChats(
            listOf(
                existing.copy(
                    title = MessengerRu.alertsTitle,
                    lastBody = text,
                    lastKind = "text",
                    lastAt = now,
                    unread = existing.unread + 1
                )
            )
        )
    }

    fun release() {
        voicePlayer.stop()
        voiceRecorder.stop(delete = true)
    }

    private fun applyEnabled(on: Boolean): Boolean {
        prefs.enabledCached = on
        _enabled.value = on
        return on
    }

    private fun MessengerChat.toRow() = MessengerChatRow(
        id = id,
        kind = kind,
        title = title,
        peerId = peerId,
        groupId = groupId,
        avatarUrl = avatarUrl,
        isOwner = isOwner,
        lastBody = lastBody,
        lastKind = lastKind,
        lastAt = lastAt,
        unread = unread
    )

    private fun MessengerChatRow.toChat() = MessengerChat(
        id = id,
        kind = kind,
        title = title,
        peerId = peerId,
        groupId = groupId,
        avatarUrl = avatarUrl,
        isOwner = isOwner,
        lastBody = lastBody,
        lastKind = lastKind,
        lastAt = lastAt,
        unread = unread
    )

    private fun MessengerMessage.toRow() = MessengerMessageRow(
        id = id,
        chatId = chatId,
        senderId = senderId,
        senderName = senderName,
        kind = kind,
        body = body,
        voiceDurationMs = voiceDurationMs,
        createdAt = createdAt,
        mine = mine,
        editedAt = editedAt,
        deleted = deleted
    )

    private fun MessengerMessageRow.toMessage() = MessengerMessage(
        id = id,
        chatId = chatId,
        senderId = senderId,
        senderName = senderName,
        kind = kind,
        body = body,
        voiceDurationMs = voiceDurationMs,
        createdAt = createdAt,
        mine = mine,
        editedAt = editedAt,
        deleted = deleted
    )
}
