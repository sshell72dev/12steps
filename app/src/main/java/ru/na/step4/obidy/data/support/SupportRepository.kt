package ru.na.step4.obidy.data.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.life.LifeBoardStore
import ru.na.step4.obidy.data.spiritual.SpiritualRatingStore
import ru.na.step4.obidy.data.spiritual.SpiritualSource

class SupportRepository(
    private val prefs: JournalPrefs,
    private val spiritual: SpiritualRatingStore? = null,
    private val lifeBoard: LifeBoardStore? = null
) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    val userId: String get() = prefs.deviceId
    val userName: String get() = prefs.name.trim().ifBlank { SupportRu.anonymous }
    val adminCode: String get() = if (prefs.isAdmin) prefs.adminCode else ""
    val isAdmin: Boolean get() = prefs.isAdmin

    suspend fun refreshUnread() {
        val count = withContext(Dispatchers.IO) {
            SupportClient.unread(userId, adminCode)
        }
        _unread.value = count
    }

    suspend fun send(
        screen: String,
        route: String,
        body: String,
        belonging: String = SupportBelonging.SCREEN,
        kind: String = SupportKind.BUG
    ): Boolean {
        val (scopedScreen, scopedRoute) = SupportBelonging.resolve(belonging, route, screen)
        val text = body.trim()
        if (SupportKind.normalize(kind) == SupportKind.IDEA) {
            lifeBoard?.addIdea(text)
        }
        val ticket = withContext(Dispatchers.IO) {
            SupportClient.create(
                userId,
                userName,
                scopedScreen,
                scopedRoute,
                text,
                belonging = belonging,
                kind = kind
            )
        }
        if (SupportKind.normalize(kind) == SupportKind.IDEA) {
            lifeBoard?.addIdea(
                text,
                sourceId = ticket?.id?.takeIf { it > 0L }?.let { "support:$it" }.orEmpty()
            )
        }
        refreshUnread()
        if (ticket != null) {
            spiritual?.applyTask(SpiritualSource.SUPPORT)
        }
        return ticket != null
    }

    suspend fun importIdeasToBoard() {
        val board = lifeBoard ?: return
        val tickets = mineBundle().tickets.filter {
            SupportKind.normalize(it.kind) == SupportKind.IDEA
        }
        tickets.forEach { ticket ->
            val text = ticket.preview.trim().ifBlank {
                ticket.messages.firstOrNull { msg -> msg.fromUser }?.body.orEmpty()
            }.trim()
            if (text.isNotBlank()) {
                board.addIdea(text, sourceId = "support:${ticket.id}")
            }
        }
    }

    suspend fun mine(): List<SupportTicket> = mineBundle().tickets

    suspend fun mineBundle(): SupportListResult = withContext(Dispatchers.IO) {
        SupportClient.listMine(userId)
    }

    suspend fun inbox(): List<SupportTicket> = withContext(Dispatchers.IO) {
        SupportClient.inbox(adminCode)
    }

    suspend fun open(id: Long): SupportTicket? = withContext(Dispatchers.IO) {
        val ticket = SupportClient.one(id, userId, adminCode) ?: return@withContext null
        if (adminCode.isBlank()) {
            val marked = SupportClient.markRead(id, userId, adminCode)
            refreshUnread()
            marked ?: ticket
        } else {
            refreshUnread()
            ticket
        }
    }

    suspend fun reply(id: Long, body: String, complete: Boolean = false): SupportTicket? =
        withContext(Dispatchers.IO) {
            val ticket = SupportClient.reply(id, body.trim(), userId, adminCode, complete)
            refreshUnread()
            ticket
        }

    suspend fun setStatus(id: Long, status: String): SupportTicket? = withContext(Dispatchers.IO) {
        val ticket = SupportClient.setStatus(id, status, adminCode)
        refreshUnread()
        ticket
    }

    suspend fun markMessageRead(messageId: Long): SupportTicket? = withContext(Dispatchers.IO) {
        val ticket = SupportClient.markMessageRead(messageId, adminCode)
        refreshUnread()
        ticket
    }

    suspend fun complete(id: Long): SupportTicket? = withContext(Dispatchers.IO) {
        val ticket = SupportClient.complete(id, adminCode)
        refreshUnread()
        ticket
    }

    suspend fun deleteMessage(messageId: Long): SupportTicket? = withContext(Dispatchers.IO) {
        val ticket = SupportClient.deleteMessage(messageId, adminCode)
        refreshUnread()
        ticket
    }

    suspend fun editMessage(messageId: Long, body: String): SupportTicket? = withContext(Dispatchers.IO) {
        val ticket = SupportClient.editMessage(messageId, body.trim(), adminCode)
        refreshUnread()
        ticket
    }

    suspend fun deleteTicket(id: Long): Boolean = withContext(Dispatchers.IO) {
        val ok = SupportClient.deleteTicket(id, userId, adminCode)
        refreshUnread()
        ok
    }
}
