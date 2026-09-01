package ru.na.steps12.voice

data class VoiceOption(
    val id: String,
    val provider: String,
    val label: String,
    val gender: String,
    val version: Int? = null
) {
    val key: String get() = "$provider:$id"
}

object VoiceCatalog {
    val azureRussian = listOf(
        VoiceOption("ru-RU-SvetlanaNeural", "azure", "Светлана · Azure, русский", "female"),
        VoiceOption("ru-RU-DmitryNeural", "azure", "Дмитрий · Azure, русский", "male"),
        VoiceOption("ru-RU-DariyaNeural", "azure", "Дарья · Azure, русский", "female")
    )

    val vapi = listOf(
        VoiceOption("Elliot", "vapi", "Elliot · Vapi", "male", 2),
        VoiceOption("Savannah", "vapi", "Savannah · Vapi", "female", 2),
        VoiceOption("Emma", "vapi", "Emma · Vapi", "female", 2),
        VoiceOption("Clara", "vapi", "Clara · Vapi", "female", 2),
        VoiceOption("Nico", "vapi", "Nico · Vapi", "male", 2),
        VoiceOption("Kai", "vapi", "Kai · Vapi", "male", 2),
        VoiceOption("Layla", "vapi", "Layla · Vapi", "female", 2),
        VoiceOption("Sid", "vapi", "Sid · Vapi", "male", 2),
        VoiceOption("Naina", "vapi", "Naina · Vapi", "female", 2),
        VoiceOption("Rohan", "vapi", "Rohan · Vapi", "male"),
        VoiceOption("Sagar", "vapi", "Sagar · Vapi", "male", 2),
        VoiceOption("Godfrey", "vapi", "Godfrey · Vapi", "male", 2),
        VoiceOption("Neil", "vapi", "Neil · Vapi", "male", 2)
    )

    val all: List<VoiceOption> = azureRussian + vapi

    val default: VoiceOption = azureRussian.first()

    fun find(provider: String, id: String): VoiceOption =
        all.firstOrNull { it.provider == provider && it.id == id }
            ?: if (provider == "android" && id.isNotBlank()) {
                VoiceOption(
                    id = id,
                    provider = "android",
                    label = "Телефон · $id",
                    gender = inferGender(id)
                )
            } else {
                default
            }

    fun findByKey(key: String): VoiceOption {
        val parts = key.split(":", limit = 2)
        return if (parts.size == 2) find(parts[0], parts[1]) else default
    }

    fun inferGender(name: String): String {
        val n = name.lowercase()
        val female = listOf(
            "female", "woman", "girl", "svetlana", "daria", "dariya", "irina",
            "milena", "ksenia", "anna", "maria", "ruf", "rue"
        )
        val male = listOf(
            "male", "man", "boy", "dmitry", "dmitri", "ostap", "yuri", "pavel", "rud"
        )
        val isFemale = female.any { token ->
            n.contains(token) && (token.length > 3 || Regex("""(^|[-_x])$token($|[-_])""").containsMatchIn(n))
        }
        val isMale = male.any { token ->
            n.contains(token) && (token.length > 3 || Regex("""(^|[-_x])$token($|[-_])""").containsMatchIn(n))
        }
        return when {
            isFemale && !isMale -> "female"
            isMale && !isFemale -> "male"
            else -> "unknown"
        }
    }
}
