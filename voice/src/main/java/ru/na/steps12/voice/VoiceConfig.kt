package ru.na.steps12.voice

data class VoiceConfig(
    val publicKey: String = "",
    val assistantId: String = "",
    val provider: String = VoiceCatalog.default.provider,
    val voiceId: String = VoiceCatalog.default.id,
    val speed: Float = 1f,
    val userOverride: Boolean = false
) {
    val option: VoiceOption get() = VoiceCatalog.find(provider, voiceId)
    val configured: Boolean get() = publicKey.isNotBlank()

    fun toVapiVoice(): Map<String, Any> {
        if (provider == "android") {
            val azureId = if (option.gender == "male") "ru-RU-DmitryNeural" else "ru-RU-SvetlanaNeural"
            return mapOf(
                "provider" to "azure",
                "voiceId" to azureId,
                "speed" to speed.toDouble()
            )
        }
        val map = linkedMapOf<String, Any>(
            "provider" to provider,
            "voiceId" to voiceId,
            "speed" to speed.toDouble()
        )
        if (provider == "vapi") {
            option.version?.let { map["version"] = it }
            map["language"] = VoiceI18n.speechTag.substringBefore('-').ifBlank { "en" }
        }
        return map
    }
}
