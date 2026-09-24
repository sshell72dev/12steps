package ru.na.step4.obidy.ui.messenger

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.messenger.MessengerChallenge
import ru.na.step4.obidy.data.messenger.MessengerChat
import ru.na.step4.obidy.data.messenger.MessengerContact
import ru.na.step4.obidy.data.messenger.MessengerGroupInfo
import ru.na.step4.obidy.data.messenger.MessengerJoinResult
import ru.na.step4.obidy.data.messenger.MessengerMessage
import ru.na.step4.obidy.data.messenger.MessengerRepository
import ru.na.step4.obidy.data.messenger.MessengerResult
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.data.messenger.MessengerTopic

data class MessengerGate(
    val enabled: Boolean = true,
    val ready: Boolean = false,
    val needsNickname: Boolean = false,
    val loading: Boolean = true
)

class MessengerViewModel(
    val repository: MessengerRepository
) : ViewModel() {
    val gate = MutableStateFlow(MessengerGate())
    val chats: StateFlow<List<MessengerChat>> = repository.chats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val contacts: StateFlow<List<MessengerContact>> = repository.contacts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val pairToken = repository.pairToken
    val error = repository.error
    val playingId = repository.voicePlayer.playingId
    val myAvatarUrl = repository.myAvatarUrl
    val groupRefresh = repository.groupRefresh

    var chatTitle: String = ""
        private set
    var chatGroupId: String = ""
        private set
    var chatAvatarUrl: String = ""
        private set
    var qrKind: String = "pair"
        private set
    var qrToken: String = ""
        private set
    var qrTitle: String = ""
        private set
    var qrGroupId: String = ""
        private set

    private val _groupInfo = MutableStateFlow<MessengerGroupInfo?>(null)
    val groupInfo: StateFlow<MessengerGroupInfo?> = _groupInfo.asStateFlow()

    private val _topics = MutableStateFlow<List<MessengerTopic>>(emptyList())
    val topics: StateFlow<List<MessengerTopic>> = _topics.asStateFlow()

    private val _challenges = MutableStateFlow<List<MessengerChallenge>>(emptyList())
    val challenges: StateFlow<List<MessengerChallenge>> = _challenges.asStateFlow()

    private var hubPoll: Job? = null
    private var chatPoll: Job? = null

    init {
        viewModelScope.launch { bootstrap() }
    }

    suspend fun bootstrap() {
        gate.value = gate.value.copy(loading = true)
        val enabled = repository.refreshEnabled()
        if (!enabled) {
            gate.value = MessengerGate(enabled = false, loading = false)
            return
        }
        if (repository.needsNickname()) {
            gate.value = MessengerGate(enabled = true, needsNickname = true, loading = false)
            return
        }
        val ready = repository.ensureRegistered()
        gate.value = MessengerGate(
            enabled = repository.enabled.value,
            ready = ready,
            needsNickname = !ready && repository.needsNickname(),
            loading = false
        )
        if (ready) {
            repository.refreshChats()
            repository.refreshContacts()
            _challenges.value = repository.refreshChallenges()
        }
    }

    fun saveNickname(name: String) {
        viewModelScope.launch {
            gate.value = gate.value.copy(loading = true)
            val ok = repository.saveNickname(name)
            gate.value = MessengerGate(
                enabled = repository.enabled.value,
                ready = ok,
                needsNickname = !ok,
                loading = false
            )
            if (ok) {
                repository.refreshChats()
                repository.refreshContacts()
                _challenges.value = repository.refreshChallenges()
            }
        }
    }

    fun startHubPolling() {
        hubPoll?.cancel()
        hubPoll = viewModelScope.launch {
            while (isActive) {
                repository.refreshChats()
                _challenges.value = repository.refreshChallenges()
                delay(8_000)
            }
        }
    }

    fun stopHubPolling() {
        hubPoll?.cancel()
        hubPoll = null
    }

    fun openChat(chat: MessengerChat) {
        chatTitle = chat.title
        chatGroupId = chat.groupId
        chatAvatarUrl = chat.avatarUrl
    }

    fun openChat(id: String, title: String, groupId: String, avatarUrl: String = "") {
        chatTitle = title
        chatGroupId = groupId
        chatAvatarUrl = avatarUrl
    }

    fun startChatPolling(chatId: String) {
        chatPoll?.cancel()
        chatPoll = viewModelScope.launch {
            while (isActive) {
                repository.refreshMessages(chatId)
                delay(2_500)
            }
        }
    }

    fun stopChatPolling() {
        chatPoll?.cancel()
        chatPoll = null
    }

    fun sendText(chatId: String, body: String) {
        val text = body.trim()
        if (text.isBlank()) return
        viewModelScope.launch { repository.sendText(chatId, text) }
    }

    fun sendVoice(chatId: String, file: File, durationMs: Int) {
        viewModelScope.launch { repository.sendVoice(chatId, file, durationMs) }
    }

    fun editMessage(messageId: Long, body: String) {
        val text = body.trim()
        if (text.isBlank()) return
        viewModelScope.launch { repository.editMessage(messageId, text) }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    fun playVoice(message: MessengerMessage) {
        viewModelScope.launch {
            val file = repository.voiceFile(message) ?: return@launch
            repository.voicePlayer.toggle(message.id, file)
        }
    }

    fun preparePairQr() {
        qrKind = "pair"
        qrTitle = MessengerRu.myQrTitle
        qrToken = pairToken.value
        qrGroupId = ""
    }

    fun rotatePair() {
        viewModelScope.launch {
            qrToken = repository.rotatePairToken()
        }
    }

    suspend fun consumePendingInvite(): MessengerResult<MessengerJoinResult>? {
        val token = repository.consumeInvite() ?: return null
        return repository.join(token)
    }

    fun joinToken(token: String, onDone: (MessengerResult<MessengerJoinResult>) -> Unit) {
        viewModelScope.launch { onDone(repository.join(token)) }
    }

    fun createGroup(name: String, userIds: List<String>, onDone: (MessengerJoinResult?) -> Unit) {
        viewModelScope.launch { onDone(repository.createGroup(name, userIds)) }
    }

    fun joinChallenge(key: String, onDone: (MessengerJoinResult?) -> Unit) {
        viewModelScope.launch {
            val result = repository.joinChallenge(key)
            _challenges.value = repository.refreshChallenges()
            onDone(result)
        }
    }

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            val info = repository.loadGroup(groupId)
            _groupInfo.value = info
            if (info != null) {
                qrKind = "group"
                qrToken = info.token
                qrTitle = info.name
                qrGroupId = info.id
            }
        }
    }

    fun addMembers(groupId: String, userIds: List<String>) {
        viewModelScope.launch {
            if (repository.addMembers(groupId, userIds)) loadGroup(groupId)
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            if (repository.uploadMyAvatar(uri)) loadGroup(groupInfo.value?.id.orEmpty())
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch { repository.deleteMyAvatar() }
    }

    fun renameGroup(groupId: String, name: String) {
        viewModelScope.launch {
            if (repository.renameGroup(groupId, name)) loadGroup(groupId)
        }
    }

    fun deleteGroup(groupId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (repository.deleteGroup(groupId)) {
                _groupInfo.value = null
                onDone()
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            if (repository.removeMember(groupId, userId)) loadGroup(groupId)
        }
    }

    fun loadTopics(groupId: String) {
        viewModelScope.launch { _topics.value = repository.loadTopics(groupId) }
    }

    fun createTopic(groupId: String, name: String) {
        viewModelScope.launch { repository.createTopic(groupId, name) }
    }

    fun renameTopic(groupId: String, topicId: String, name: String) {
        viewModelScope.launch { repository.renameTopic(groupId, topicId, name) }
    }

    fun deleteTopic(groupId: String, topicId: String) {
        viewModelScope.launch { repository.deleteTopic(groupId, topicId) }
    }

    fun uploadGroupAvatar(groupId: String, uri: Uri) {
        viewModelScope.launch {
            if (repository.uploadGroupAvatar(groupId, uri)) loadGroup(groupId)
        }
    }

    fun deleteGroupAvatar(groupId: String) {
        viewModelScope.launch {
            if (repository.deleteGroupAvatar(groupId)) loadGroup(groupId)
        }
    }

    suspend fun avatarBytes(url: String): ByteArray? = repository.avatarBytes(url)

    fun rotateGroupQr(groupId: String) {
        viewModelScope.launch {
            val token = repository.rotateGroupToken(groupId) ?: return@launch
            qrToken = token
            _groupInfo.value = _groupInfo.value?.copy(token = token)
        }
    }

    fun openGroupQr(info: MessengerGroupInfo) {
        qrKind = "group"
        qrToken = info.token
        qrTitle = info.name
        qrGroupId = info.id
    }

    fun clearError() = repository.clearError()

    override fun onCleared() {
        stopHubPolling()
        stopChatPolling()
        repository.release()
        super.onCleared()
    }

    companion object {
        fun factory(repository: MessengerRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MessengerViewModel(repository) as T
            }
        }
    }
}
