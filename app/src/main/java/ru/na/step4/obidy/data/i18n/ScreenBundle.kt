package ru.na.step4.obidy.data.i18n

enum class ScreenBundle {
    HOME,
    PROFILE,
    JOURNAL,
    JOURNAL_TREE,
    ANALYSIS,
    PSYCH,
    INVENTORY,
    SUPPORT,
    LIFE,
    LOCK,
    SPIRITUAL,
    ACTIVITY,
    MESSENGER,
    COMMON;

    fun keys(): Set<String> = when (this) {
        COMMON -> UiSourceKeys.common
        HOME -> UiSourceKeys.home + UiSourceKeys.life + UiSourceKeys.common
        PROFILE -> UiSourceKeys.profile + UiSourceKeys.common
        JOURNAL -> UiSourceKeys.journal + UiSourceKeys.common
        JOURNAL_TREE -> UiSourceKeys.journal + UiSourceKeys.treePrefixKeys() + UiSourceKeys.common
        ANALYSIS -> UiSourceKeys.analysis + UiSourceKeys.analysisCatalogKeys() + UiSourceKeys.common
        PSYCH -> UiSourceKeys.psych + UiSourceKeys.common
        INVENTORY -> UiSourceKeys.inventory + UiSourceKeys.common
        SUPPORT -> UiSourceKeys.support + UiSourceKeys.common
        LIFE -> UiSourceKeys.life + UiSourceKeys.common
        LOCK -> UiSourceKeys.lock + UiSourceKeys.common
        SPIRITUAL -> UiSourceKeys.spiritual + UiSourceKeys.common
        ACTIVITY -> UiSourceKeys.activity + UiSourceKeys.common
        MESSENGER -> UiSourceKeys.messenger + UiSourceKeys.common
    }
}
