package ru.na.step4.obidy.data.i18n

import java.util.Locale

object LocaleHelper {
    private val KEEP_REGION = setOf("pt-br", "zh-tw", "zh-hk", "zh-cn", "en-gb", "es-mx", "es-ar")

    fun normalize(raw: String): String {
        val tag = raw.trim().replace('_', '-')
        if (tag.isBlank()) return "en"
        val locale = runCatching { Locale.forLanguageTag(tag) }.getOrNull()
        val language = (locale?.language ?: tag.substringBefore('-')).lowercase(Locale.ROOT)
        if (language.isBlank()) return "en"
        val region = locale?.country?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
        val candidate = if (region != null) "$language-$region" else language
        return when {
            candidate in KEEP_REGION -> {
                val parts = candidate.split('-')
                "${parts[0]}-${parts[1].uppercase(Locale.ROOT)}"
            }
            language == "zh" && region == "tw" -> "zh-TW"
            language == "zh" && region == "hk" -> "zh-TW"
            language == "pt" && region == "br" -> "pt-BR"
            else -> language
        }
    }

    fun deviceLanguage(): String = normalize(Locale.getDefault().toLanguageTag())

    fun isRussian(code: String): Boolean {
        val n = normalize(code)
        return n == "ru" || n.startsWith("ru-")
    }

    fun toLocale(code: String): Locale {
        val n = normalize(code)
        return runCatching { Locale.forLanguageTag(n) }.getOrElse { Locale(n.substringBefore('-')) }
    }

    fun speechTag(code: String): String {
        val locale = toLocale(code)
        val lang = locale.language.ifBlank { "en" }
        val country = locale.country.ifBlank {
            when (lang) {
                "ru" -> "RU"
                "en" -> "US"
                "uk" -> "UA"
                "de" -> "DE"
                "fr" -> "FR"
                "es" -> "ES"
                "pt" -> "BR"
                "it" -> "IT"
                "pl" -> "PL"
                "tr" -> "TR"
                "ar" -> "SA"
                "zh" -> "CN"
                "ja" -> "JP"
                "ko" -> "KR"
                else -> lang.uppercase(Locale.ROOT)
            }
        }
        return "$lang-$country"
    }

    fun languageInstruction(code: String): String {
        if (isRussian(code)) {
            return "\nОБЯЗАТЕЛЬНО: отвечай полностью на русском языке. Не смешивай языки.\n"
        }
        val label = AppLanguages.find(code)?.englishName ?: normalize(code)
        return "\nОБЯЗАТЕЛЬНО: отвечай полностью на языке: $label ($code).\n" +
            "Не смешивай языки. Весь текст ответа (включая заголовки) должен быть на этом языке.\n"
    }
}
