package ru.na.steps12.voice

/** App binds this so VoiceRu can resolve translations without depending on the app module. */
object VoiceI18n {
    @Volatile
    var resolver: ((key: String, sourceRu: String) -> String)? = null

    @Volatile
    var speechTag: String = "ru-RU"

    fun t(key: String, sourceRu: String): String =
        resolver?.invoke(key, sourceRu) ?: sourceRu
}
