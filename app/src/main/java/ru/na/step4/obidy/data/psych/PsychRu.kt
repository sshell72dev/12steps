package ru.na.step4.obidy.data.psych

import ru.na.step4.obidy.data.i18n.I18n

object PsychRu {
    val intro: String get() = I18n.t("psych.intro", "Ситуации по дню — запиши прямо в моменте любую ситуацию: мысль, действие, событие или страх — вообще всё, что важно. Получи мгновенную консультацию ИИ‑ассистента или пройди самоанализ из 3–5 вопросов, которые сгенерируются под твою ситуацию. Ты увидишь полезные рекомендации извне — то, что сложно заметить, когда ты внутри. Можно отложить и вернуться позже. Это инструмент спокойствия и душевного равновесия, не терапия и не медицина.")
    val welcome: String get() = I18n.t("psych.welcome", "Привет! Это твой новый незаменимый помощник.\n\nЗдесь всё просто: пишешь ситуацию — мысль, стресс, страх или действие — и получаешь немедленную помощь.")
    val askName: String get() = I18n.t("psych.askName", "Давай познакомимся. Как тебя зовут?")
    val skip: String get() = I18n.t("psych.skip", "Пропустить")
    val nameTooLong: String get() = I18n.t("psych.nameTooLong", "Похоже, ты описал ситуацию, а не имя — так тоже можно.\nИмя можно указать позже в профиле или пропустить.")
    val describe: String get() = I18n.t("psych.describe", "Опиши проблему")
    val record: String get() = I18n.t("psych.record", "Записать ситуацию")
    val view: String get() = I18n.t("psych.view", "Просмотр ситуаций")
    val settings: String get() = I18n.t("psych.settings", "Настройки")
    val mainMenu: String get() = I18n.t("psych.mainMenu", "Главное меню")
    val profile: String get() = I18n.t("psych.profile", "Мой профиль")
    val topics: String get() = I18n.t("psych.topics", "Темы")
    val aiSettings: String get() = I18n.t("psych.aiSettings", "Настройки ИИ")
    val reminders: String get() = I18n.t("psych.reminders", "Напоминания")
    val fillProfile: String get() = I18n.t("psych.fillProfile", "Заполнить анкету")
    val furtherActions: String get() = I18n.t("psych.furtherActions", "Дальнейшие действия")
    val analyze: String get() = I18n.t("psych.analyze", "Разобрать ситуацию")
    val recommend: String get() = I18n.t("psych.recommend", "Рекомендации по ситуации")
    val work: String get() = I18n.t("psych.work", "Проработка ситуации")
    val postpone: String get() = I18n.t("psych.postpone", "Отложить")
    val share: String get() = I18n.t("psych.share", "Поделиться текстом")
    val readMore: String get() = I18n.t("psych.readMore", "Читать далее")
    val speak: String get() = I18n.t("psych.speak", "Озвучить Голосовым")
    val speakRecord: String get() = I18n.t("psych.speakRecord", "Озвучить запись")
    val speakQuestion: String get() = I18n.t("psych.speakQuestion", "Озвучить вопрос")
    val adminPrompt: String get() = I18n.t("psych.adminPrompt", "Промпт к ИИ")
    val copyPrompt: String get() = I18n.t("psych.copyPrompt", "Копировать промпт")
    val promptCopied: String get() = I18n.t("psych.promptCopied", "Промпт скопирован")
    val yourRecord: String get() = I18n.t("psych.yourRecord", "Ваша запись")
    val moreTopics: String get() = I18n.t("psych.moreTopics", "Ещё темы")
    val noTopic: String get() = I18n.t("psych.noTopic", "Без темы")
    val noHistory: String get() = I18n.t("psych.noHistory", "Без истории")
    val addTopic: String get() = I18n.t("psych.addTopic", "Добавить тему")
    val topicPickHint: String get() = I18n.t("psych.topicPickHint", "Можно выбрать несколько тем — ситуация попадёт в хронологию каждой.")
    val confirmTopics: String get() = I18n.t("psych.confirmTopics", "Продолжить с выбранными")
    val selectedCount: String get() = I18n.t("psych.selectedCount", "Выбрано тем: %1\$d")
    val topicChronology: String get() = I18n.t("psych.topicChronology", "Хронология историй")
    val topicEmptyChronology: String get() = I18n.t("psych.topicEmptyChronology", "По этой теме ещё нет историй.")
    val newTopicHint: String get() = I18n.t("psych.newTopicHint", "Новая тема")
    val startWork: String get() = I18n.t("psych.startWork", "Начать проработку")
    val continueWork: String get() = I18n.t("psych.continueWork", "Продолжить")
    val finish: String get() = I18n.t("psych.finish", "Завершить")
    val finishSituation: String get() = I18n.t("psych.finishSituation", "Завершить ситуацию")
    val startNew: String get() = I18n.t("psych.startNew", "Начать новую")
    val assistant: String get() = I18n.t("psych.assistant", "Ассистент ИИ по ситуации")
    val postponed: String get() = I18n.t("psych.postponed", "Отложенные")
    val completed: String get() = I18n.t("psych.completed", "Проработанные")
    val day: String get() = I18n.t("psych.day", "День")
    val week: String get() = I18n.t("psych.week", "Неделя")
    val pickDate: String get() = I18n.t("psych.pickDate", "Дата ДД.ММ или ДД.ММ.ГГГГ")
    val topicSaved: String get() = I18n.t("psych.topicSaved", "Ситуация сохранена. Выбери одну или несколько тем.")
    val waitingQuestion: String get() = I18n.t("psych.waitingQuestion", "Ожидаю следующий вопрос")
    val waitingAnswer: String get() = I18n.t("psych.waitingAnswer", "Формирую ответ")
    val waitingVoice: String get() = I18n.t("psych.waitingVoice", "Формируется голосовое…")
    val voiceHint: String get() = I18n.t("psych.voiceHint", "Обычно до минуты. Можно пользоваться другими функциями.")
    val quotaLeft: String get() = I18n.t("psych.quotaLeft", "Сегодня осталось обращений к ИИ: %1\$d из %2\$d.")
    val quotaGone: String get() = I18n.t("psych.quotaGone", "Лимит запросов к ИИ на сегодня исчерпан (%1\$d/%2\$d).")
    val teaserHint: String get() = I18n.t(
        "psych.teaserHint",
        "Развёрнутое продолжение («Слепые зоны» и далее) — в Premium. Базовая модель flash уже даёт полноценную опору в работе."
    )
    val upsell: String get() = I18n.t(
        "psych.upsell",
        "Premium — не столько привилегия, сколько вклад в развитие приложения и благодарность за инструмент выздоровления.\n\n" +
            "Базовая модель flash прекрасно справляется со всеми функциями. С Premium подключается более глубокая аналитическая модель и удобства:\n" +
            "- «Моя личность» (персональный контекст)\n" +
            "- Развёрнутые ответы и критичный стиль\n" +
            "- Больше обращений к ИИ в день\n" +
            "- Напоминания с гибким интервалом\n" +
            "- Голос, просмотр за неделю и вопросы по одному\n\n" +
            "Сумма небольшая — это поддержка, а не плата «за доступ к работе»."
    )
    val paywallTitle: String get() = I18n.t("psych.paywallTitle", "Premium")
    val paywallBody: String get() = I18n.t(
        "psych.paywallBody",
        "Flash уже закрывает рабочие сценарии приложения. Premium — небольшой вклад в развитие и возможность сказать «спасибо» за инструмент эффективной работы над выздоровлением. В знак признательности — аналитическая модель и расширенные удобства."
    )
    val paywallPrice: String get() = I18n.t("psych.paywallPrice", "Сумма поддержки: %1\$s ₽")
    val paywallSupport: String get() = I18n.t("psych.paywallSupport", "Поддержать · Premium")
    val paywallOpening: String get() = I18n.t("psych.paywallOpening", "Открываем страницу оплаты…")
    val paywallWaiting: String get() = I18n.t(
        "psych.paywallWaiting",
        "После оплаты вернитесь сюда — Premium активируется автоматически."
    )
    val paywallThanks: String get() = I18n.t(
        "psych.paywallThanks",
        "Спасибо за вклад. Premium уже активен на этом устройстве."
    )
    val paywallHint: String get() = I18n.t(
        "psych.paywallHint",
        "Оплата через ЮKassa (СБП или карта). Это вклад в развитие, не подписка магазина."
    )
    val grantPro: String get() = I18n.t("psych.grantPro", "Выдать Premium на 30 дней (тест)")
    val compact: String get() = I18n.t("psych.compact", "Компактный")
    val expanded: String get() = I18n.t("psych.expanded", "Развёрнутый")
    val neutral: String get() = I18n.t("psych.neutral", "Нейтральный")
    val critical: String get() = I18n.t("psych.critical", "Критичный")
    val simpleQ: String get() = I18n.t("psych.simpleQ", "Простые вопросы")
    val hardQ: String get() = I18n.t("psych.hardQ", "Сложные вопросы")
    val shortQ: String get() = I18n.t("psych.shortQ", "Короткие вопросы")
    val longQ: String get() = I18n.t("psych.longQ", "Длинные вопросы")
    val personality: String get() = I18n.t("psych.personality", "Моя личность")
    val personalityOn: String get() = I18n.t("psych.personalityOn", "Сбор портрета включён")
    val personalityOff: String get() = I18n.t("psych.personalityOff", "Сбор портрета выключен")
    val personalityEdit: String get() = I18n.t("psych.personalityEdit", "Портрет")
    val format: String get() = I18n.t("psych.format", "Формат ответа")
    val style: String get() = I18n.t("psych.style", "Стиль ответа")
    val workQs: String get() = I18n.t("psych.workQs", "Вопросы проработки")
    val questionLimits: String get() = I18n.t("psych.questionLimits", "Сколько вопросов задавать")
    val questionLimitsHint: String get() = I18n.t(
        "psych.questionLimitsHint",
        "Целое число от 1 до 30. Дополнительные вопросы — после первого сообщения. Вопросы проработки — отдельно."
    )
    val dialogueExtraQs: String get() = I18n.t(
        "psych.dialogueExtraQs",
        "Дополнительные вопросы после первого сообщения"
    )
    val workQuestionCount: String get() = I18n.t("psych.workQuestionCount", "Вопросы для проработки")
    val reminderOn: String get() = I18n.t("psych.reminderOn", "Напоминания включены")
    val reminderOff: String get() = I18n.t("psych.reminderOff", "Напоминания выключены")
    val intervalHours: String get() = I18n.t("psych.intervalHours", "Интервал, часов")
    val quietHours: String get() = I18n.t("psych.quietHours", "Тихие часы")
    val voiceOn: String get() = I18n.t("psych.voiceOn", "Голосовой ввод и озвучка")
    val topicsOn: String get() = I18n.t("psych.topicsOn", "Темы включены")
    val topicsOff: String get() = I18n.t("psych.topicsOff", "Темы выключены")
    val name: String get() = I18n.t("psych.name", "Имя")
    val birth: String get() = I18n.t("psych.birth", "Год рождения")
    val place: String get() = I18n.t("psych.place", "Место")
    val program: String get() = I18n.t("psych.program", "Программа")
    val about: String get() = I18n.t("psych.about", "О себе")
    val timezone: String get() = I18n.t("psych.timezone", "Смещение времени, минут от UTC")
    val customProgram: String get() = I18n.t("psych.customProgram", "Своё название")
    val deleteTopic: String get() = I18n.t("psych.deleteTopic", "Удалить тему?")
    val deleteTopicBody: String get() = I18n.t("psych.deleteTopicBody", "Тема будет удалена. Ситуации останутся.")
    val topicMemory: String get() = I18n.t("psych.topicMemory", "Сжатая память темы")
    val idleTitle: String get() = I18n.t("psych.idleTitle", "Продолжить?")
    val idleBody: String get() = I18n.t("psych.idleBody", "Диалог давно без ответа.")
    val emptyView: String get() = I18n.t("psych.emptyView", "За этот период записей нет.")
    val countRecords: String get() = I18n.t("psych.countRecords", "Записей: %1\$d")
    val send: String get() = I18n.t("psych.send", "Отправить")
    val meetNice: String get() = I18n.t("psych.meetNice", "Приятно познакомиться, %1\$s. Теперь отправь своё первое сообщение — опиши ситуацию, мысль или страх…")
    val skipName: String get() = I18n.t("psych.skipName", "друг")
    val reminderFallback: String get() = I18n.t("psych.reminderFallback", "Я готов. Просто отправь мне то, что тебя сейчас волнует.")
    val psychologistName: String get() = I18n.t("psych.psychologistName", "Психолог")
    val reminderHow: String get() = I18n.t(
        "psych.reminderHow",
        "Напоминания приходят в чат «Оповещение» в мессенджере и дублируются уведомлением на заставке и в шторке телефона."
    )
    val reminderWhere: String get() = I18n.t(
        "psych.reminderWhere",
        "Включить, выбрать интервал и тихие часы можно на этом экране. Кнопка «Показать уведомление сейчас» сразу отправит сообщение в чат «Оповещение» и в шторку — на этом экране ничего не всплывает."
    )
    val reminderNext: String get() = I18n.t("psych.reminderNext", "Следующее напоминание: %1\$s")
    val reminderNextOff: String get() = I18n.t("psych.reminderNextOff", "Напоминания выключены — уведомления не приходят.")
    val reminderTest: String get() = I18n.t("psych.reminderTest", "Показать уведомление сейчас")
    val reminderPermissionOff: String get() = I18n.t(
        "psych.reminderPermissionOff",
        "Телефон блокирует уведомления приложения. Разрешите их в настройках Android, иначе напоминания не появятся в шторке."
    )
    val reminderOpenSettings: String get() = I18n.t("psych.reminderOpenSettings", "Открыть настройки уведомлений")
    val quietStart: String get() = I18n.t("psych.quietStart", "Тихие часы с (0–23)")
    val quietEnd: String get() = I18n.t("psych.quietEnd", "Тихие часы до (0–23)")
    val interval6: String get() = I18n.t("psych.interval6", "Каждые 6 ч")
    val interval12: String get() = I18n.t("psych.interval12", "Каждые 12 ч")
    val interval24: String get() = I18n.t("psych.interval24", "Раз в сутки")
    val busy: String get() = I18n.t("psych.busy", "Уже формирую ответ.")
    val proOnly: String get() = I18n.t("psych.proOnly", "Эта возможность доступна в Premium.")
    val speakIntro: String get() = I18n.t("psych.speakIntro", "Я так понял твою ситуацию:")
    val speakBridge: String get() = I18n.t("psych.speakBridge", "Если я неправильно понял ситуацию — запиши её заново. Если всё верно — продолжаю.")
    val speakAnalyze: String get() = I18n.t("psych.speakAnalyze", "Дальше разбор ситуации.")
    val speakRecommend: String get() = I18n.t("psych.speakRecommend", "Дальше рекомендации.")
    val speakAssistant: String get() = I18n.t("psych.speakAssistant", "Дальше ответ ассистента.")
    val listening: String get() = I18n.t("psych.listening", "Слушаю…")
    val notProVoice: String get() = I18n.t("psych.notProVoice", "Голос доступен в Premium, если голосовой сервис включён.")
    val weekPro: String get() = I18n.t("psych.weekPro", "Просмотр за неделю доступен в Premium.")
    val oneByOnePro: String get() = I18n.t("psych.oneByOnePro", "Вопросы по одному — в Premium. Сейчас пакет из 3–5 вопросов.")
    val remainingUnlimited: String get() = I18n.t("psych.remainingUnlimited", "Сегодня обращения к ИИ без лимита.")
    val disclaimer: String get() = I18n.t("psych.disclaimer", "Это инструмент саморефлексии, не терапия, не медицина и не юриспруденция.")
    val eyebrow: String get() = I18n.t("psych.eyebrow", "Рефлексия в моменте")
    val copyAll: String get() = I18n.t("psych.copyAll", "Одним текстом")
    val oneByOne: String get() = I18n.t("psych.oneByOne", "По одной")
    val viewModeHint: String get() = I18n.t(
        "psych.viewModeHint",
        "По одной — листать ситуации. Одним текстом — все записи сразу, удобно копировать."
    )
    val viewIndex: String get() = I18n.t("psych.viewIndex", "%1\$d из %2\$d")
    val prevSituation: String get() = I18n.t("psych.prevSituation", "Предыдущая")
    val nextSituation: String get() = I18n.t("psych.nextSituation", "Следующая")
    val today: String get() = I18n.t("psych.today", "Сегодня")
    val open: String get() = I18n.t("psych.open", "Открыть")
    val saveName: String get() = I18n.t("psych.saveName", "Сохранить")
    val topicName: String get() = I18n.t("psych.topicName", "Название темы")
    val none: String get() = I18n.t("psych.none", "Не указано")
    val offsetHint: String get() = I18n.t("psych.offsetHint", "Например 180 для Москвы")
    val channel: String get() = I18n.t("psych.channel", "psychologist")
}
