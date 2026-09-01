package ru.na.step4.obidy.data.ai

import java.net.URLEncoder
import org.json.JSONObject

object PremiumClient {
    data class CreateResult(
        val paymentId: String,
        val confirmationUrl: String,
        val amount: String = "",
        val premiumDays: Int = 365
    )

    data class StatusResult(
        val premium: Boolean,
        val expiresAtUnix: Long = 0L,
        val paymentStatus: String = "",
        val premiumDays: Int = 365
    )

    fun createPayment(deviceId: String, returnUrl: String): AiHttp.Result {
        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("return_url", returnUrl)
        return AiHttp.post("/api/v1/premium/create-payment", payload, readTimeoutMs = 30_000)
    }

    fun parseCreate(raw: AiHttp.Result): Pair<CreateResult?, String?> {
        return when (raw) {
            is AiHttp.Result.Err -> null to raw.message
            is AiHttp.Result.Ok -> {
                val obj = AiHttp.parseObject(raw.body)
                if (raw.code !in 200..299) {
                    val err = obj.optString("error")
                    val msg = when (err) {
                        "not_configured" -> "Оплата пока не настроена на сервере"
                        "unauthorized" -> AiHttp.errorMessage(obj)
                        else -> obj.optString("detail").ifBlank {
                            AiHttp.errorMessage(obj, "Не удалось создать платёж")
                        }
                    }
                    return null to msg
                }
                val url = obj.optString("confirmation_url")
                val id = obj.optString("payment_id")
                if (url.isBlank() || id.isBlank()) {
                    return null to "Сервер не вернул ссылку на оплату"
                }
                CreateResult(
                    paymentId = id,
                    confirmationUrl = url,
                    amount = obj.optString("amount"),
                    premiumDays = obj.optInt("premium_days", 365)
                ) to null
            }
        }
    }

    fun status(deviceId: String, paymentId: String? = null): AiHttp.Result {
        return if (paymentId.isNullOrBlank()) {
            val q = URLEncoder.encode(deviceId, Charsets.UTF_8.name())
            AiHttp.get("/api/v1/premium/status?device_id=$q")
        } else {
            val payload = JSONObject()
                .put("device_id", deviceId)
                .put("payment_id", paymentId)
            AiHttp.post("/api/v1/premium/status", payload, readTimeoutMs = 30_000)
        }
    }

    fun parseStatus(raw: AiHttp.Result): Pair<StatusResult?, String?> {
        return when (raw) {
            is AiHttp.Result.Err -> null to raw.message
            is AiHttp.Result.Ok -> {
                val obj = AiHttp.parseObject(raw.body)
                if (raw.code !in 200..299) {
                    return null to AiHttp.errorMessage(obj, "Не удалось проверить статус Premium")
                }
                StatusResult(
                    premium = obj.optBoolean("premium", false),
                    expiresAtUnix = obj.optLong("expires_at_unix", 0L),
                    paymentStatus = obj.optString("payment_status"),
                    premiumDays = obj.optInt("premium_days", 365)
                ) to null
            }
        }
    }
}
