package ru.na.steps12.voice

import ru.na.steps12.voice.VoiceI18n

object VoiceRu {
    val dictation: String get() = VoiceI18n.t("voice.dictation", "Ввести голосом")
    val speak: String get() = VoiceI18n.t("voice.speak", "Озвучить")
    val stop: String get() = VoiceI18n.t("voice.stop", "Стоп")
    val listening: String get() = VoiceI18n.t("voice.listening", "Слушаю…")
    val settingsTitle: String get() = VoiceI18n.t("voice.settingsTitle", "Голос")
    val settingsHint: String get() = VoiceI18n.t("voice.settingsHint", "Озвучка в приложении идёт голосами телефона (обычно мужской и женский). Azure и Vapi меняют голос живого консультанта. Ключи задаются во вкладке «Голос» на сайте.")
    val voiceLabel: String get() = VoiceI18n.t("voice.voiceLabel", "Голос по умолчанию")
    val speedLabel: String get() = VoiceI18n.t("voice.speedLabel", "Скорость воспроизведения")
    val preview: String get() = VoiceI18n.t("voice.preview", "Прослушать")
    val notConfigured: String get() = VoiceI18n.t("voice.notConfigured", "Ключ Vapi ещё не задан. Админ может указать его на сайте, вкладка «Голос».")
    val previewText: String get() = VoiceI18n.t("voice.previewText", "Это проверка выбранного голоса и скорости. Текст можно вводить голосом в любом поле.")
}
