package ru.na.step4.obidy.data.i18n

import java.util.Locale

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
)

object AppLanguages {
    val common: List<AppLanguage> = listOf(
        AppLanguage("ru", "Русский", "Russian"),
        AppLanguage("en", "English", "English"),
        AppLanguage("uk", "Українська", "Ukrainian"),
        AppLanguage("be", "Беларуская", "Belarusian"),
        AppLanguage("kk", "Қазақша", "Kazakh"),
        AppLanguage("uz", "Oʻzbekcha", "Uzbek"),
        AppLanguage("ky", "Кыргызча", "Kyrgyz"),
        AppLanguage("tg", "Тоҷикӣ", "Tajik"),
        AppLanguage("hy", "Հայերեն", "Armenian"),
        AppLanguage("ka", "ქართული", "Georgian"),
        AppLanguage("az", "Azərbaycanca", "Azerbaijani"),
        AppLanguage("de", "Deutsch", "German"),
        AppLanguage("fr", "Français", "French"),
        AppLanguage("es", "Español", "Spanish"),
        AppLanguage("pt", "Português", "Portuguese"),
        AppLanguage("pt-BR", "Português (Brasil)", "Portuguese (Brazil)"),
        AppLanguage("it", "Italiano", "Italian"),
        AppLanguage("pl", "Polski", "Polish"),
        AppLanguage("cs", "Čeština", "Czech"),
        AppLanguage("sk", "Slovenčina", "Slovak"),
        AppLanguage("ro", "Română", "Romanian"),
        AppLanguage("hu", "Magyar", "Hungarian"),
        AppLanguage("bg", "Български", "Bulgarian"),
        AppLanguage("sr", "Српски", "Serbian"),
        AppLanguage("hr", "Hrvatski", "Croatian"),
        AppLanguage("sl", "Slovenščina", "Slovenian"),
        AppLanguage("el", "Ελληνικά", "Greek"),
        AppLanguage("tr", "Türkçe", "Turkish"),
        AppLanguage("ar", "العربية", "Arabic"),
        AppLanguage("he", "עברית", "Hebrew"),
        AppLanguage("fa", "فارسی", "Persian"),
        AppLanguage("hi", "हिन्दी", "Hindi"),
        AppLanguage("bn", "বাংলা", "Bengali"),
        AppLanguage("ur", "اردو", "Urdu"),
        AppLanguage("zh", "中文", "Chinese"),
        AppLanguage("zh-TW", "中文（繁體）", "Chinese (Traditional)"),
        AppLanguage("ja", "日本語", "Japanese"),
        AppLanguage("ko", "한국어", "Korean"),
        AppLanguage("vi", "Tiếng Việt", "Vietnamese"),
        AppLanguage("th", "ไทย", "Thai"),
        AppLanguage("id", "Bahasa Indonesia", "Indonesian"),
        AppLanguage("ms", "Bahasa Melayu", "Malay"),
        AppLanguage("tl", "Tagalog", "Tagalog"),
        AppLanguage("nl", "Nederlands", "Dutch"),
        AppLanguage("sv", "Svenska", "Swedish"),
        AppLanguage("no", "Norsk", "Norwegian"),
        AppLanguage("da", "Dansk", "Danish"),
        AppLanguage("fi", "Suomi", "Finnish"),
        AppLanguage("et", "Eesti", "Estonian"),
        AppLanguage("lv", "Latviešu", "Latvian"),
        AppLanguage("lt", "Lietuvių", "Lithuanian"),
        AppLanguage("sw", "Kiswahili", "Swahili")
    )

    fun find(code: String): AppLanguage? {
        val n = LocaleHelper.normalize(code)
        return common.firstOrNull { LocaleHelper.normalize(it.code) == n }
            ?: common.firstOrNull {
                LocaleHelper.normalize(it.code).substringBefore('-') == n.substringBefore('-')
            }
    }

    fun label(code: String): String {
        val found = find(code)
        return if (found != null) "${found.nativeName} (${found.code})" else code
    }

    fun search(query: String): List<AppLanguage> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return common
        return common.filter {
            it.code.lowercase(Locale.ROOT).contains(q) ||
                it.nativeName.lowercase(Locale.ROOT).contains(q) ||
                it.englishName.lowercase(Locale.ROOT).contains(q)
        }
    }
}
