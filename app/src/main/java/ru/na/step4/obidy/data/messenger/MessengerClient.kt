package ru.na.step4.obidy.data.messenger

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class MessengerClient(private val messengerId: () -> String) {

    fun statusEnabled(): MessengerResult<Boolean> {
        return when (val raw = MessengerHttp.get("/api/v1/messenger/status", "")) {
            is MessengerResult.Disabled -> MessengerResult.Ok(false)
            is MessengerResult.Err -> raw
            is MessengerResult.Ok -> {
                if (raw.value.code !in 200..299) MessengerResult.Err()
                else MessengerResult.Ok(MessengerHttp.parse(raw.value.body).optBoolean("enabled", true))
            }
        }
    }

    fun register(displayName: String): MessengerResult<Pair<MessengerUser, String>> {
        val payload = JSONObject().put("display_name", displayName)
        return map(MessengerHttp.post("/api/v1/messenger/me", messengerId(), payload)) { obj ->
            val user = parseUser(obj.optJSONObject("user"))
            user to obj.optString("pair_token")
        }
    }

    fun me(): MessengerResult<Pair<MessengerUser, String>> {
        return map(MessengerHttp.get("/api/v1/messenger/me", messengerId())) { obj ->
            val user = parseUser(obj.optJSONObject("user"))
            user to obj.optString("pair_token")
        }
    }

    fun rotatePair(): MessengerResult<String> {
        val payload = JSONObject().put("kind", "pair").put("rotate", true)
        return map(MessengerHttp.post("/api/v1/messenger/invites", messengerId(), payload)) { obj ->
            obj.optString("token")
        }
    }

    fun groupInvite(groupId: String, rotate: Boolean = false): MessengerResult<String> {
        val payload = JSONObject()
            .put("kind", "group")
            .put("group_id", groupId)
            .put("rotate", rotate)
        return map(MessengerHttp.post("/api/v1/messenger/invites", messengerId(), payload)) { obj ->
            obj.optString("token")
        }
    }

    fun join(token: String): MessengerResult<MessengerJoinResult> {
        val payload = JSONObject().put("token", token)
        return map(MessengerHttp.post("/api/v1/messenger/join", messengerId(), payload)) { obj ->
            MessengerJoinResult(
                kind = obj.optString("kind"),
                chatId = obj.optString("chat_id"),
                title = obj.optString("title"),
                groupId = obj.optString("group_id"),
                challengeKey = obj.optString("key"),
                hasTopics = obj.optBoolean("has_topics"),
                topics = parseChallengeTopics(obj.optJSONArray("topics"))
            )
        }
    }

    fun contacts(): MessengerResult<List<MessengerContact>> {
        return map(MessengerHttp.get("/api/v1/messenger/contacts", messengerId())) { obj ->
            parseContacts(obj.optJSONArray("contacts"))
        }
    }

    fun chats(): MessengerResult<List<MessengerChat>> {
        return map(MessengerHttp.get("/api/v1/messenger/chats", messengerId())) { obj ->
            parseChats(obj.optJSONArray("chats"))
        }
    }

    fun messages(chatId: String, after: Long): MessengerResult<MessengerMessages> {
        return map(
            MessengerHttp.get("/api/v1/messenger/chats/$chatId/messages?after=$after", messengerId())
        ) { obj ->
            MessengerMessages(
                items = parseMessages(obj.optJSONArray("messages")),
                pinned = obj.optJSONObject("pinned")?.let { parseMessage(it) }
            )
        }
    }

    fun sendText(
        chatId: String,
        body: String,
        replyToId: Long = 0L,
        anonymous: Boolean = false
    ): MessengerResult<MessengerMessage> {
        val payload = JSONObject().put("body", body)
        if (replyToId > 0L) payload.put("reply_to_id", replyToId)
        if (anonymous) payload.put("anonymous", true)
        return map(
            MessengerHttp.post("/api/v1/messenger/chats/$chatId/messages", messengerId(), payload)
        ) { obj ->
            parseMessage(obj.optJSONObject("message"))
        }
    }

    /** Запись в челлендж: ленту подгруппы находит сервер по ключу челленджа. */
    fun shareChallenge(key: String, body: String): MessengerResult<Unit> {
        val payload = JSONObject().put("body", body)
        return map(
            MessengerHttp.post("/api/v1/messenger/challenges/$key/share", messengerId(), payload)
        ) { }
    }

    /** Пересылка: сервер копирует текст и подписывает автора оригинала, слать body не нужно. */
    fun forwardMessage(chatId: String, messageId: Long): MessengerResult<MessengerMessage> {
        val payload = JSONObject().put("forward_message_id", messageId)
        return map(
            MessengerHttp.post("/api/v1/messenger/chats/$chatId/messages", messengerId(), payload)
        ) { obj ->
            parseMessage(obj.optJSONObject("message"))
        }
    }

    fun sendVoice(
        chatId: String,
        file: File,
        durationMs: Int,
        replyToId: Long = 0L
    ): MessengerResult<MessengerMessage> {
        return map(
            MessengerHttp.postMultipart(
                "/api/v1/messenger/chats/$chatId/voice",
                messengerId(),
                file,
                durationMs,
                replyToId
            )
        ) { obj ->
            parseMessage(obj.optJSONObject("message"))
        }
    }

    fun markRead(chatId: String, lastId: Long): MessengerResult<Unit> {
        val payload = JSONObject().put("last_id", lastId)
        return map(
            MessengerHttp.post("/api/v1/messenger/chats/$chatId/read", messengerId(), payload)
        ) { }
    }

    fun editMessage(messageId: Long, body: String): MessengerResult<MessengerMessage> {
        val payload = JSONObject().put("body", body)
        return map(
            MessengerHttp.post("/api/v1/messenger/messages/$messageId/edit", messengerId(), payload)
        ) { obj -> parseMessage(obj.optJSONObject("message")) }
    }

    fun deleteMessage(messageId: Long): MessengerResult<MessengerMessage> {
        return map(
            MessengerHttp.delete("/api/v1/messenger/messages/$messageId", messengerId())
        ) { obj -> parseMessage(obj.optJSONObject("message")) }
    }

    /** messageId = 0 снимает закреп. */
    fun pinMessage(chatId: String, messageId: Long): MessengerResult<MessengerMessage?> {
        val payload = JSONObject().put("message_id", messageId)
        return map(
            MessengerHttp.post("/api/v1/messenger/chats/$chatId/pin", messengerId(), payload)
        ) { obj ->
            obj.optJSONObject("pinned")?.let { parseMessage(it) }
        }
    }

    /** Пустой emoji снимает реакцию; повтор той же реакции тоже её снимает. */
    fun setReaction(messageId: Long, emoji: String): MessengerResult<MessengerMessage> {
        val payload = JSONObject().put("emoji", emoji)
        return map(
            MessengerHttp.post(
                "/api/v1/messenger/messages/$messageId/reaction",
                messengerId(),
                payload
            )
        ) { obj -> parseMessage(obj.optJSONObject("message")) }
    }

    fun createGroup(name: String, userIds: List<String>): MessengerResult<MessengerJoinResult> {
        val ids = JSONArray()
        userIds.forEach { ids.put(it) }
        val payload = JSONObject().put("name", name).put("user_ids", ids)
        return map(MessengerHttp.post("/api/v1/messenger/groups", messengerId(), payload)) { obj ->
            val group = obj.optJSONObject("group") ?: JSONObject()
            MessengerJoinResult(
                kind = "group",
                chatId = obj.optString("chat_id"),
                title = group.optString("name").ifBlank { name },
                groupId = group.optString("id")
            )
        }
    }

    fun challenges(): MessengerResult<List<MessengerChallenge>> {
        return map(MessengerHttp.get("/api/v1/messenger/challenges", messengerId())) { obj ->
            parseChallenges(obj.optJSONArray("challenges"))
        }
    }

    fun joinChallenge(key: String): MessengerResult<MessengerJoinResult> {
        return map(
            MessengerHttp.post("/api/v1/messenger/challenges/$key/join", messengerId(), JSONObject())
        ) { obj ->
            MessengerJoinResult(
                kind = obj.optString("kind").ifBlank { "group" },
                chatId = obj.optString("chat_id"),
                title = obj.optString("title"),
                groupId = obj.optString("group_id"),
                challengeKey = obj.optString("key").ifBlank { key },
                topics = parseChallengeTopics(obj.optJSONArray("topics"))
            )
        }
    }

    fun group(groupId: String): MessengerResult<MessengerGroupInfo> {
        return map(MessengerHttp.get("/api/v1/messenger/groups/$groupId", messengerId())) { obj ->
            val g = obj.optJSONObject("group") ?: JSONObject()
            val members = mutableListOf<MessengerContact>()
            val arr = obj.optJSONArray("members")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    members += parseContact(row)
                }
            }
            MessengerGroupInfo(
                id = g.optString("id"),
                name = g.optString("name"),
                ownerId = g.optString("owner_id"),
                isOwner = g.optBoolean("is_owner"),
                canManage = g.optBoolean("can_manage"),
                avatarUrl = g.optString("avatar_url"),
                anonymous = g.optBoolean("anonymous"),
                members = members,
                token = obj.optString("token"),
                chatId = obj.optString("chat_id")
            )
        }
    }

    fun addMembers(groupId: String, userIds: List<String>): MessengerResult<List<String>> {
        val ids = JSONArray()
        userIds.forEach { ids.put(it) }
        val payload = JSONObject().put("user_ids", ids)
        return map(
            MessengerHttp.post("/api/v1/messenger/groups/$groupId/members", messengerId(), payload)
        ) { obj ->
            val arr = obj.optJSONArray("added") ?: return@map emptyList()
            buildList {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
        }
    }

    fun uploadMyAvatar(file: File, mimeType: String): MessengerResult<MessengerUser> {
        return map(
            MessengerHttp.postFile(
                path = "/api/v1/messenger/me/avatar",
                messengerId = messengerId(),
                file = file,
                fieldName = "file",
                fileName = "avatar",
                mimeType = mimeType
            )
        ) { obj -> parseUser(obj.optJSONObject("user")) }
    }

    fun deleteMyAvatar(): MessengerResult<Unit> {
        return map(MessengerHttp.delete("/api/v1/messenger/me/avatar", messengerId())) { }
    }

    fun uploadGroupAvatar(groupId: String, file: File, mimeType: String): MessengerResult<String> {
        return map(
            MessengerHttp.postFile(
                path = "/api/v1/messenger/groups/$groupId/avatar",
                messengerId = messengerId(),
                file = file,
                fieldName = "file",
                fileName = "avatar",
                mimeType = mimeType
            )
        ) { obj -> obj.optString("avatar_url") }
    }

    fun deleteGroupAvatar(groupId: String): MessengerResult<Unit> {
        return map(
            MessengerHttp.delete("/api/v1/messenger/groups/$groupId/avatar", messengerId())
        ) { }
    }

    fun renameGroup(groupId: String, name: String): MessengerResult<String> {
        val payload = JSONObject().put("name", name)
        return map(MessengerHttp.post("/api/v1/messenger/groups/$groupId", messengerId(), payload)) { obj ->
            obj.optJSONObject("group")?.optString("name").orEmpty().ifBlank { name }
        }
    }

    /** Режим анонимности группы: её участники могут писать от лица «Анонимный». */
    fun setGroupAnonymous(groupId: String, anonymous: Boolean): MessengerResult<Boolean> {
        val payload = JSONObject().put("anonymous", anonymous)
        return map(MessengerHttp.post("/api/v1/messenger/groups/$groupId", messengerId(), payload)) { obj ->
            obj.optJSONObject("group")?.optBoolean("anonymous") ?: anonymous
        }
    }

    fun deleteGroup(groupId: String): MessengerResult<Unit> {
        return map(MessengerHttp.delete("/api/v1/messenger/groups/$groupId", messengerId())) { }
    }

    fun removeMember(groupId: String, userId: String): MessengerResult<Unit> {
        return map(
            MessengerHttp.delete("/api/v1/messenger/groups/$groupId/members/$userId", messengerId())
        ) { }
    }

    fun topics(groupId: String): MessengerResult<MessengerTopics> {
        return map(
            MessengerHttp.get("/api/v1/messenger/groups/$groupId/topics", messengerId())
        ) { obj ->
            MessengerTopics(
                items = parseTopics(obj.optJSONArray("topics")),
                canCreate = obj.optBoolean("can_create")
            )
        }
    }

    fun createTopic(groupId: String, name: String): MessengerResult<MessengerTopic> {
        val payload = JSONObject().put("name", name)
        return map(
            MessengerHttp.post("/api/v1/messenger/groups/$groupId/topics", messengerId(), payload)
        ) { obj -> parseTopic(obj.optJSONObject("topic")) }
    }

    fun renameTopic(groupId: String, topicId: String, name: String): MessengerResult<String> {
        val payload = JSONObject().put("name", name)
        return map(
            MessengerHttp.post(
                "/api/v1/messenger/groups/$groupId/topics/$topicId",
                messengerId(),
                payload
            )
        ) { obj ->
            obj.optJSONObject("topic")?.optString("name").orEmpty().ifBlank { name }
        }
    }

    fun deleteTopic(groupId: String, topicId: String): MessengerResult<Unit> {
        return map(
            MessengerHttp.delete("/api/v1/messenger/groups/$groupId/topics/$topicId", messengerId())
        ) { }
    }

    fun uploadTopicAvatar(
        groupId: String,
        topicId: String,
        file: File,
        mimeType: String
    ): MessengerResult<String> {
        return map(
            MessengerHttp.postFile(
                path = "/api/v1/messenger/groups/$groupId/topics/$topicId/avatar",
                messengerId = messengerId(),
                file = file,
                fieldName = "file",
                fileName = "avatar",
                mimeType = mimeType
            )
        ) { obj -> obj.optString("avatar_url") }
    }

    fun deleteTopicAvatar(groupId: String, topicId: String): MessengerResult<Unit> {
        return map(
            MessengerHttp.delete(
                "/api/v1/messenger/groups/$groupId/topics/$topicId/avatar",
                messengerId()
            )
        ) { }
    }

    fun imageBytes(url: String): MessengerResult<ByteArray> {
        return MessengerHttp.getBytes(url, messengerId())
    }

    fun downloadVoice(messageId: Long): MessengerResult<ByteArray> {
        return MessengerHttp.getBytes("/api/v1/messenger/voice/$messageId", messengerId())
    }

    private fun <T> map(
        raw: MessengerResult<MessengerHttp.Response>,
        parse: (JSONObject) -> T
    ): MessengerResult<T> {
        return when (raw) {
            is MessengerResult.Disabled -> MessengerResult.Disabled
            is MessengerResult.Err -> raw
            is MessengerResult.Ok -> {
                val obj = MessengerHttp.parse(raw.value.body)
                if (raw.value.code !in 200..299) {
                    MessengerResult.Err(errorText(obj))
                } else {
                    runCatching { MessengerResult.Ok(parse(obj)) }
                        .getOrElse { MessengerResult.Err() }
                }
            }
        }
    }

    private fun errorText(obj: JSONObject): String {
        return when (obj.optString("error")) {
            "disabled" -> MessengerRu.disabledTitle
            "self_invite" -> MessengerRu.selfInvite
            "invite_not_found", "bad_token" -> MessengerRu.badQr
            "file_too_large" -> MessengerRu.photoTooLarge
            "bad_image" -> MessengerRu.photoBadFormat
            "challenge_locked" -> MessengerRu.groupLocked
            "owner_immutable" -> MessengerRu.ownerImmutable
            "topic_not_found" -> MessengerRu.topicNotFound
            "name_required" -> MessengerRu.nameRequired
            "forbidden" -> MessengerRu.noRights
            else -> MessengerRu.error
        }
    }

    private fun parseChallenges(arr: JSONArray?): List<MessengerChallenge> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                add(
                    MessengerChallenge(
                        key = row.optString("key"),
                        name = row.optString("name"),
                        groupId = row.optString("group_id"),
                        chatId = row.optString("chat_id"),
                        joined = row.optBoolean("joined"),
                        members = row.optInt("members"),
                        topics = parseChallengeTopics(row.optJSONArray("topics"))
                    )
                )
            }
        }
    }

    private fun parseChallengeTopics(arr: JSONArray?): List<MessengerChallengeTopic> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val key = row.optString("key")
                if (key.isBlank()) continue
                add(
                    MessengerChallengeTopic(
                        key = key,
                        name = row.optString("name"),
                        chatId = row.optString("chat_id")
                    )
                )
            }
        }
    }

    private fun parseUser(obj: JSONObject?): MessengerUser {
        val row = obj ?: return MessengerUser()
        return MessengerUser(
            id = row.optString("id"),
            displayName = row.optString("display_name"),
            avatarUrl = row.optString("avatar_url")
        )
    }

    private fun parseContacts(arr: JSONArray?): List<MessengerContact> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                add(parseContact(row))
            }
        }
    }

    private fun parseContact(row: JSONObject) = MessengerContact(
        id = row.optString("id"),
        displayName = row.optString("display_name"),
        avatarUrl = row.optString("avatar_url")
    )

    private fun parseTopics(arr: JSONArray?): List<MessengerTopic> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                add(parseTopic(arr.optJSONObject(i)))
            }
        }
    }

    private fun parseTopic(obj: JSONObject?): MessengerTopic {
        val row = obj ?: JSONObject()
        return MessengerTopic(
            id = row.optString("id"),
            name = row.optString("name"),
            chatId = row.optString("chat_id"),
            isGeneral = row.optBoolean("is_default"),
            unread = row.optInt("unread"),
            lastBody = row.optString("last_body"),
            lastKind = row.optString("last_kind"),
            lastAt = row.optLong("last_at"),
            key = row.optString("key"),
            avatarUrl = row.optString("avatar_url"),
            canManage = row.optBoolean("can_manage")
        )
    }

    private fun parseChats(arr: JSONArray?): List<MessengerChat> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val pinned = row.optJSONObject("pinned")
                add(
                    MessengerChat(
                        id = row.optString("id"),
                        kind = row.optString("kind"),
                        title = row.optString("title"),
                        peerId = row.optString("peer_id"),
                        groupId = row.optString("group_id"),
                        avatarUrl = row.optString("avatar_url"),
                        isOwner = row.optBoolean("is_owner"),
                        lastBody = row.optString("last_body"),
                        lastKind = row.optString("last_kind"),
                        lastAt = row.optLong("last_at"),
                        unread = row.optInt("unread"),
                        hasTopics = row.optBoolean("has_topics"),
                        anonymous = row.optBoolean("anonymous"),
                        pinnedId = pinned?.optLong("id") ?: 0L,
                        pinnedKind = pinned?.optString("kind").orEmpty(),
                        pinnedBody = pinned?.optString("body").orEmpty(),
                        pinnedSender = pinned?.optString("sender_name").orEmpty()
                    )
                )
            }
        }
    }

    private fun parseMessages(arr: JSONArray?): List<MessengerMessage> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                add(parseMessage(row))
            }
        }
    }

    private fun parseMessage(obj: JSONObject?): MessengerMessage {
        val row = obj ?: JSONObject()
        val reply = row.optJSONObject("reply_to")
        return MessengerMessage(
            id = row.optLong("id"),
            chatId = row.optString("chat_id"),
            senderId = row.optString("sender_id"),
            senderName = row.optString("sender_name"),
            kind = row.optString("kind"),
            body = row.optString("body"),
            voiceDurationMs = row.optInt("voice_duration_ms"),
            createdAt = row.optLong("created_at"),
            mine = row.optBoolean("mine"),
            editedAt = row.optLong("edited_at"),
            deleted = row.optBoolean("deleted"),
            replyToId = reply?.optLong("id") ?: 0L,
            replySenderName = reply?.optString("sender_name").orEmpty(),
            replyBody = reply?.optString("body").orEmpty(),
            replyKind = reply?.optString("kind").orEmpty(),
            replyVoiceMs = reply?.optInt("voice_duration_ms") ?: 0,
            replyDeleted = reply?.optBoolean("deleted") ?: false,
            forwardFrom = row.optString("forward_from"),
            reactions = parseReactions(row.optJSONArray("reactions"))
        )
    }

    private fun parseReactions(arr: JSONArray?): List<MessengerReaction> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                add(
                    MessengerReaction(
                        emoji = row.optString("emoji"),
                        count = row.optInt("count"),
                        mine = row.optBoolean("mine")
                    )
                )
            }
        }
    }
}
