package ru.na.step4.obidy.data.book

import ru.na.step4.obidy.data.i18n.I18n

object BookRu {
    val title: String get() = I18n.t("book.title", "Моя книга")
    val hint: String get() = I18n.t(
        "book.hint",
        "Книга пишется по главам: текст главы можно надиктовать голосом и прослушать."
    )
    val empty: String get() = I18n.t(
        "book.empty",
        "Пока нет книг. Нажмите «плюс» внизу, чтобы добавить первую."
    )
    val addBook: String get() = I18n.t("book.addBook", "Добавить книгу")
    val bookTitle: String get() = I18n.t("book.bookTitle", "Название книги")
    val bookTitleHint: String get() = I18n.t("book.bookTitleHint", "Например: моя история")
    val create: String get() = I18n.t("book.create", "Создать")
    val cancel: String get() = I18n.t("book.cancel", "Отмена")
    val chapters: String get() = I18n.t("book.chapters", "Главы")
    val chaptersEmpty: String get() = I18n.t(
        "book.chaptersEmpty",
        "В книге пока нет глав. Создайте первую главу."
    )
    val chaptersCount: String get() = I18n.t("book.chaptersCount", "Глав: %1\$d")
    val newChapter: String get() = I18n.t("book.newChapter", "Новая глава")
    val chapterTitle: String get() = I18n.t("book.chapterTitle", "Название главы")
    val chapterText: String get() = I18n.t("book.chapterText", "Текст главы")
    val save: String get() = I18n.t("book.save", "Сохранить")
    val saved: String get() = I18n.t("book.saved", "Сохранено")
    val chapterDefault: String get() = I18n.t("book.chapterDefault", "Глава %1\$d")
    val emptyText: String get() = I18n.t("book.emptyText", "Текст пока пустой")
    val bookMissing: String get() = I18n.t("book.bookMissing", "Книга не найдена.")
    val back: String get() = I18n.t("book.back", "Назад")
    val fixErrors: String get() = I18n.t("book.fixErrors", "Исправить ошибки")
    val fixTitle: String get() = I18n.t("book.fixTitle", "Проверка текста")
    val fixChecking: String get() = I18n.t("book.fixChecking", "Проверяем текст…")
    val fixOriginal: String get() = I18n.t("book.fixOriginal", "Ваш текст")
    val fixFixed: String get() = I18n.t("book.fixFixed", "Исправленный текст")
    val fixNothing: String get() = I18n.t("book.fixNothing", "Ошибок не найдено.")
    val fixCount: String get() = I18n.t("book.fixCount", "Исправлений: %1\$d")
    val fixMarked: String get() = I18n.t("book.fixMarked", "Отмечено: %1\$d")
    val fixAcceptAll: String get() = I18n.t("book.fixAcceptAll", "Принять все исправления")
    val fixAcceptSelected: String get() = I18n.t("book.fixAcceptSelected", "Принять отмеченные")
    val fixCancel: String get() = I18n.t("book.fixCancel", "Отменить исправления")
    val fixDone: String get() = I18n.t("book.fixDone", "Исправления применены")
    val fixError: String get() = I18n.t("book.fixError", "Не удалось проверить текст. Попробуйте ещё раз.")
    val fixWas: String get() = I18n.t("book.fixWas", "Было")
    val fixBecame: String get() = I18n.t("book.fixBecame", "Стало")

    fun chapterName(order: Int): String = chapterDefault.format(order)
}
