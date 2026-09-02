package ru.na.step4.obidy.data.ai

import org.json.JSONObject

object AppConfigClient {
    data class Config(
        val premiumPriceRub: String = "199",
        val premiumDays: Int = 365,
        val paymentsEnabled: Boolean = false,
        val messengerEnabled: Boolean = true,
        val psychDialogueExtra: Int = 5,
        val psychWorkQuestions: Int = 5
    )

    fun fetch(): Config {
        return when (val raw = AiHttp.get("/api/v1/app-config")) {
            is AiHttp.Result.Err -> Config()
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) return Config()
                val obj = runCatching { JSONObject(raw.body) }.getOrNull() ?: return Config()
                Config(
                    premiumPriceRub = obj.optString("premium_price_rub", "199").ifBlank { "199" },
                    premiumDays = obj.optInt("premium_days", 365),
                    paymentsEnabled = obj.optBoolean("premium_payments_enabled", false),
                    messengerEnabled = obj.optBoolean("messenger_enabled", true),
                    psychDialogueExtra = obj.optInt("psych_dialogue_extra", 5).coerceIn(1, 30),
                    psychWorkQuestions = obj.optInt("psych_work_questions", 5).coerceIn(1, 30)
                )
            }
        }
    }
}
