package ru.na.step4.obidy.data.support

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp

object SupportClient {
    fun create(
        userId: String,
        userName: String,
        screen: String,
        screenRoute: String,
        body: String,
        belonging: String = SupportBelonging.SCREEN,
        kind: String = SupportKind.BUG
    ): SupportTicket? {
        val payload = JSONObject()
            .put("user_id", userId)
            .put("user_name", userName)
            .put("screen", screen)
            .put("screen_route", screenRoute)
            .put("body", body)
            .put("belonging", belonging)
            .put("kind", kind)
        return when (val raw = AiHttp.post("/api/v1/support", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun listMine(userId: String): SupportListResult {
        val path = "/api/v1/support?user_id=${enc(userId)}"
        return when (val raw = AiHttp.get(path, 20_000)) {
            is AiHttp.Result.Err -> SupportListResult()
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) SupportListResult()
                else parseList(AiHttp.parseObject(raw.body))
            }
        }
    }

    fun inbox(adminCode: String): List<SupportTicket> {
        val path = "/api/v1/support/inbox?code=${enc(adminCode)}"
        return when (val raw = AiHttp.get(path, 20_000)) {
            is AiHttp.Result.Err -> emptyList()
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) emptyList()
                else parseTickets(AiHttp.parseObject(raw.body).optJSONArray("tickets"))
            }
        }
    }

    fun one(ticketId: Long, userId: String, adminCode: String): SupportTicket? {
        val path = buildString {
            append("/api/v1/support/$ticketId?")
            if (adminCode.isNotBlank()) append("code=${enc(adminCode)}")
            else append("user_id=${enc(userId)}")
        }
        return when (val raw = AiHttp.get(path, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun reply(
        ticketId: Long,
        body: String,
        userId: String,
        adminCode: String,
        complete: Boolean = false
    ): SupportTicket? {
        val payload = JSONObject().put("body", body)
        if (adminCode.isNotBlank()) {
            payload.put("code", adminCode)
            if (complete) payload.put("complete", true)
        } else {
            payload.put("user_id", userId)
        }
        return when (val raw = AiHttp.post("/api/v1/support/$ticketId/reply", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun markRead(ticketId: Long, userId: String, adminCode: String): SupportTicket? {
        val payload = JSONObject()
        if (adminCode.isNotBlank()) payload.put("code", adminCode)
        else payload.put("user_id", userId)
        return when (val raw = AiHttp.post("/api/v1/support/$ticketId/read", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun markMessageRead(messageId: Long, adminCode: String): SupportTicket? {
        val payload = JSONObject().put("code", adminCode)
        return when (val raw = AiHttp.post("/api/v1/support/message/$messageId/read", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun complete(ticketId: Long, adminCode: String): SupportTicket? {
        val payload = JSONObject().put("code", adminCode)
        return when (val raw = AiHttp.post("/api/v1/support/$ticketId/complete", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun setStatus(ticketId: Long, status: String, adminCode: String): SupportTicket? {
        val payload = JSONObject()
            .put("code", adminCode)
            .put("status", status)
        return when (val raw = AiHttp.post("/api/v1/support/$ticketId/status", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun deleteMessage(messageId: Long, adminCode: String): SupportTicket? {
        val payload = JSONObject().put("code", adminCode)
        return when (val raw = AiHttp.post("/api/v1/support/message/$messageId/delete", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun editMessage(messageId: Long, body: String, adminCode: String): SupportTicket? {
        val payload = JSONObject()
            .put("code", adminCode)
            .put("body", body)
        return when (val raw = AiHttp.post("/api/v1/support/message/$messageId/edit", payload, 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) null
                else parseTicket(AiHttp.parseObject(raw.body).optJSONObject("ticket"))
            }
        }
    }

    fun deleteTicket(ticketId: Long, userId: String, adminCode: String): Boolean {
        val payload = JSONObject()
        if (adminCode.isNotBlank()) payload.put("code", adminCode)
        else payload.put("user_id", userId)
        return when (val raw = AiHttp.post("/api/v1/support/$ticketId/delete", payload, 20_000)) {
            is AiHttp.Result.Err -> false
            is AiHttp.Result.Ok -> raw.code in 200..299
        }
    }

    fun unread(userId: String, adminCode: String): Int {
        val path = buildString {
            append("/api/v1/support/unread?")
            if (adminCode.isNotBlank()) append("code=${enc(adminCode)}")
            else append("user_id=${enc(userId)}")
        }
        return when (val raw = AiHttp.get(path, 15_000)) {
            is AiHttp.Result.Err -> 0
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) 0
                else AiHttp.parseObject(raw.body).optInt("count")
            }
        }
    }

    fun formatWhen(raw: String): String {
        if (raw.isBlank()) return nowLabel()
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val local = SimpleDateFormat("d MMMM yyyy, HH:mm", ru.na.step4.obidy.data.i18n.I18n.locale()).apply {
            timeZone = TimeZone.getDefault()
        }
        val parsed = runCatching { utc.parse(raw.take(19)) }.getOrNull() ?: return raw
        return local.format(parsed)
    }

    fun nowLabel(): String =
        SimpleDateFormat("d MMMM yyyy, HH:mm", ru.na.step4.obidy.data.i18n.I18n.locale()).format(Date())

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun parseList(obj: JSONObject): SupportListResult = SupportListResult(
        tickets = parseTickets(obj.optJSONArray("tickets")),
        topicCounts = parseTopicCounts(obj.optJSONObject("topic_counts"))
    )

    private fun parseTickets(arr: JSONArray?): List<SupportTicket> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { parseTicket(arr.optJSONObject(it)) }
    }

    private fun parseTopicCounts(obj: JSONObject?): Map<String, Int> {
        if (obj == null) return emptyMap()
        return SupportTopic.all.associateWith { obj.optInt(it, 0) }
    }

    private fun parseTicket(obj: JSONObject?): SupportTicket? {
        if (obj == null) return null
        val messages = obj.optJSONArray("messages")
        val status = SupportStatus.normalize(obj.optString("status"))
        val statusLabel = obj.optString("status_label").ifBlank { SupportStatus.label(status) }
        val belonging = SupportBelonging.normalize(obj.optString("belonging"))
        val belongingLabel = obj.optString("belonging_label").ifBlank {
            SupportBelonging.label(belonging)
        }
        val kind = SupportKind.normalize(obj.optString("kind"))
        val kindLabel = obj.optString("kind_label").ifBlank {
            SupportKind.label(kind)
        }
        return SupportTicket(
            id = obj.optLong("id"),
            userId = obj.optString("user_id"),
            userName = obj.optString("user_name"),
            screen = obj.optString("screen"),
            screenRoute = obj.optString("screen_route"),
            createdAt = obj.optString("created_at"),
            updatedAt = obj.optString("updated_at"),
            adminRead = obj.optBoolean("admin_read", true),
            userRead = obj.optBoolean("user_read", true),
            status = status,
            statusLabel = statusLabel,
            belonging = belonging,
            belongingLabel = belongingLabel,
            kind = kind,
            kindLabel = kindLabel,
            adminSource = obj.optString("admin_source"),
            adminSourceLabel = obj.optString("admin_source_label"),
            preview = obj.optString("preview"),
            messages = if (messages == null) emptyList() else {
                (0 until messages.length()).map { i ->
                    val m = messages.optJSONObject(i) ?: JSONObject()
                    SupportMessage(
                        id = m.optLong("id"),
                        author = m.optString("author"),
                        body = m.optString("body"),
                        createdAt = m.optString("created_at"),
                        editedAt = m.optString("edited_at"),
                        edited = m.optBoolean("edited", m.optString("edited_at").isNotBlank()),
                        adminRead = m.optBoolean(
                            "admin_read",
                            m.optString("author") != "user"
                        )
                    )
                }
            }
        )
    }

    fun shareText(ticket: SupportTicket): String = buildString {
        appendLine("${ticket.kindLabel} · ${ticket.userName.ifBlank { SupportRu.anonymous }}")
        appendLine("${SupportRu.status}: ${ticket.statusLabel}")
        appendLine("${SupportRu.kind}: ${ticket.kindLabel}")
        appendLine("${SupportRu.belonging}: ${ticket.belongingLabel}")
        appendLine("${SupportRu.screen}: ${ticket.screen}")
        if (ticket.adminSourceLabel.isNotBlank()) {
            appendLine("${SupportRu.adminSource}: ${ticket.adminSourceLabel}")
        }
        appendLine("${SupportRu.date}: ${formatWhen(ticket.createdAt)}")
        appendLine()
        if (ticket.messages.isEmpty()) {
            append(ticket.preview)
        } else {
            ticket.messages.forEachIndexed { index, msg ->
                if (index > 0) appendLine().appendLine("---").appendLine()
                val who = when {
                    msg.fromSystem -> SupportRu.system
                    msg.fromAdmin -> SupportRu.admin
                    else -> SupportRu.you
                }
                appendLine("$who · ${formatWhen(msg.createdAt)}")
                append(msg.body)
            }
        }
    }
}
