package ru.na.step4.obidy.data.messenger

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
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
    val voicePlayer = MessengerVoicePlayer()
    val voiceRecorder = MessengerVoiceRecorder(appContext)

    private val _enabled = MutableStateFlow(prefs.enabledCached)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _pairToken = MutableStateFlow("")
    val pairToken: StateFlow<String> = _pairToken.asStateFlow()

    private val _pendingInvite = MutableStateFlow<String?>(null)
    val pendingInvite: StateFlow<String?> = _pendingInvite.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val chats: Flow<List<MessengerChat>> = dao.observeChats().map { rows ->
        rows.map { it.toChat() }
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
        val after = dao.lastMessageId(chatId)
        when (val result = client.messages(chatId, after)) {
            is MessengerResult.Ok -> {
                if (result.value.isNotEmpty()) {
                    dao.upsertMessages(result.value.map { it.toRow() })
                    client.markRead(chatId, result.value.maxOf { it.id })
                }
            }
            is MessengerResult.Disabled -> applyEnabled(false)
            is MessengerResult.Err -> _error.value = result.message.ifBlank { MessengerRu.error }
        }
    }

    suspend fun sendText(chatId: String, body: String): Boolean = withContext(Dispatchers.IO) {
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

    fun clearError() {
        _error.value = null
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
        mine = mine
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
        mine = mine
    )
}
