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
                challengeKey = obj.optString("key")
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

    fun messages(chatId: String, after: Long): MessengerResult<List<MessengerMessage>> {
        return map(
            MessengerHttp.get("/api/v1/messenger/chats/$chatId/messages?after=$after", messengerId())
        ) { obj ->
            parseMessages(obj.optJSONArray("messages"))
        }
    }

    fun sendText(chatId: String, body: String): MessengerResult<MessengerMessage> {
        val payload = JSONObject().put("body", body)
        return map(
            MessengerHttp.post("/api/v1/messenger/chats/$chatId/messages", messengerId(), payload)
        ) { obj ->
            parseMessage(obj.optJSONObject("message"))
        }
    }

    fun sendVoice(chatId: String, file: File, durationMs: Int): MessengerResult<MessengerMessage> {
        return map(
            MessengerHttp.postMultipart("/api/v1/messenger/chats/$chatId/voice", messengerId(), file, durationMs)
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
                challengeKey = obj.optString("key").ifBlank { key }
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
                    members += MessengerContact(row.optString("id"), row.optString("display_name"))
                }
            }
            MessengerGroupInfo(
                id = g.optString("id"),
                name = g.optString("name"),
                ownerId = g.optString("owner_id"),
                isOwner = g.optBoolean("is_owner"),
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
                        members = row.optInt("members")
                    )
                )
            }
        }
    }

    private fun parseUser(obj: JSONObject?): MessengerUser {
        val row = obj ?: return MessengerUser()
        return MessengerUser(row.optString("id"), row.optString("display_name"))
    }

    private fun parseContacts(arr: JSONArray?): List<MessengerContact> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                add(MessengerContact(row.optString("id"), row.optString("display_name")))
            }
        }
    }

    private fun parseChats(arr: JSONArray?): List<MessengerChat> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                add(
                    MessengerChat(
                        id = row.optString("id"),
                        kind = row.optString("kind"),
                        title = row.optString("title"),
                        peerId = row.optString("peer_id"),
                        groupId = row.optString("group_id"),
                        isOwner = row.optBoolean("is_owner"),
                        lastBody = row.optString("last_body"),
                        lastKind = row.optString("last_kind"),
                        lastAt = row.optLong("last_at"),
                        unread = row.optInt("unread")
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
        return MessengerMessage(
            id = row.optLong("id"),
            chatId = row.optString("chat_id"),
            senderId = row.optString("sender_id"),
            senderName = row.optString("sender_name"),
            kind = row.optString("kind"),
            body = row.optString("body"),
            voiceDurationMs = row.optInt("voice_duration_ms"),
            createdAt = row.optLong("created_at"),
            mine = row.optBoolean("mine")
        )
    }
}
