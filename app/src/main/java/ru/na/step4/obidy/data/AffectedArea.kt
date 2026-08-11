package ru.na.step4.obidy.data

import ru.na.step4.obidy.Ru

enum class AffectedArea(val label: String) {
    SELF_ESTEEM(Ru.selfEsteem),
    SECURITY(Ru.security),
    AMBITIONS(Ru.ambitions),
    PERSONAL_RELATIONS(Ru.personal),
    SEX_RELATIONS(Ru.sex),
    POCKETBOOK(Ru.money);

    companion object {
        fun fromKeys(keys: String): Set<AffectedArea> {
            if (keys.isBlank()) return emptySet()
            return keys.split(",")
                .mapNotNull { key -> entries.find { it.name == key } }
                .toSet()
        }

        fun toKeys(areas: Set<AffectedArea>): String =
            areas.joinToString(",") { it.name }
    }
}
