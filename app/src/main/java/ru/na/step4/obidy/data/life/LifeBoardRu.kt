package ru.na.step4.obidy.data.life

import ru.na.step4.obidy.data.i18n.I18n

object LifeBoardRu {
    val goals: String get() = I18n.t("life.goals", "Цели")
    val goalsBody: String get() = I18n.t("life.goalsBody", "К чему идёте. Цели учитываются в ответах ИИ.")
    val ideas: String get() = I18n.t("life.ideas", "Идеи")
    val ideasBody: String get() = I18n.t("life.ideasBody", "Замыслы и предложения, которые хотите развить.")
    val calendar: String get() = I18n.t("life.calendar", "Календарь")
    val calendarBody: String get() = I18n.t("life.calendarBody", "События и даты. Отметьте, что уже сделано.")
    val notes: String get() = I18n.t("life.notes", "Заметки")
    val notesBody: String get() = I18n.t("life.notesBody", "Короткие записи для себя.")
    val statusInProgress: String get() = I18n.t("life.statusInProgress", "В работе")
    val statusDone: String get() = I18n.t("life.statusDone", "Выполнено")
    val add: String get() = I18n.t("life.add", "Добавить")
    val title: String get() = I18n.t("life.title", "Название")
    val titleHintGoal: String get() = I18n.t("life.titleHintGoal", "Например: 90 дней чистоты")
    val titleHintIdea: String get() = I18n.t("life.titleHintIdea", "Кратко сформулируйте идею")
    val titleHintEvent: String get() = I18n.t("life.titleHintEvent", "Название события")
    val titleHintNote: String get() = I18n.t("life.titleHintNote", "Заголовок заметки")
    val body: String get() = I18n.t("life.body", "Описание")
    val bodyHint: String get() = I18n.t("life.bodyHint", "Подробности, если нужны")
    val date: String get() = I18n.t("life.date", "Дата")
    val pickDate: String get() = I18n.t("life.pickDate", "Выбрать дату")
    val emptyGoals: String get() = I18n.t("life.emptyGoals", "Пока нет целей. Добавьте первую — она попадёт в промпты ИИ.")
    val emptyIdeas: String get() = I18n.t("life.emptyIdeas", "Пока нет идей.")
    val emptyEvents: String get() = I18n.t("life.emptyEvents", "Пока нет событий.")
    val emptyNotes: String get() = I18n.t("life.emptyNotes", "Пока нет заметок.")
    val emptyDone: String get() = I18n.t("life.emptyDone", "Пока ничего не отмечено как выполненное.")
    val deleteTitle: String get() = I18n.t("life.deleteTitle", "Удалить запись?")
}
