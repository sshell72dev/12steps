# 12 шагов

Android-приложение с тремя разделами:

1. **Работа по 12 Шагам по Руководству** — дневник: Шаг → Глава → Точка. Дерево берётся из `categories-tree.json`. В главе **4 шаг · Обиды** как дополнение открывается прежний инвентарь обид (без изменений).
2. **Самоанализ** — ежедневные самоанализы по вопросам из бота.
3. **Электронный психолог** — запись ситуации в моменте, живой диалог, разбор, рекомендации и проработка.

Исходное приложение «4 шаг · Обиды» не ломалось: инвентарь остался отдельным блоком внутри 4 шага.

## Что уже работает

- Главный экран с тремя разделами и общей анкетой
- Дневник 12 шагов по JSON: выбор места, записи на телефоне, общая «Моя личность», помощь и анализ ИИ (PRO)
- 4 шаг: инвентарь обид (список, ситуации, 13 вопросов, импорт, голосовой консультант Vapi)
- Самоанализ: 10 шаг, mini, шаги 1–3, 12 вопросов, чистый день; история и шаринг на устройстве
- Электронный психолог: онбординг, запись ситуации, живой вопрос, разбор / рекомендации / проработка, общая анкета, настройки ИИ, темы с мультивыбором и хронологией историй, напоминания, просмотр за день/неделю, голос, квоты и тизер «Слепые зоны»
- Единые имя, анкета и портрет «Моя личность» для всех разделов: ИИ не спрашивает имя повторно, если оно уже заполнено
- Защита входа паролем и отпечатком

## Стек

- **Kotlin**, Jetpack Compose, Room, Navigation, WorkManager
- Записи дневника и общая анкета — JSON / SharedPreferences на устройстве
- **Vapi** — голосовой/текстовый консультант в блоке обид
- ИИ — через `https://12stepsapp.luch-rehab.ru` (`/api/v1/analyze` и `/api/v1/psych`)

## Конфигурация

Ключи задаются в `local.properties` (файл не коммитится):

```properties
VAPI_PUBLIC_KEY=...
VAPI_ASSISTANT_ID=...
ANALYSIS_API_URL=https://12stepsapp.luch-rehab.ru
ANALYSIS_API_TOKEN=...
```

## Premium · ЮKassa (самозанятый / НПД)

Оплата разовой суммы из админки (`premium_price_rub`) идёт через ЮKassa **вне Google Play Billing** — осознанно для РФ/НПД. Ключи магазина хранятся только на сервере.

### Кабинет ЮKassa

1. Подключите магазин (режим самозанятого), возьмите **shopId** и **secretKey**.
2. HTTP-уведомления (webhook): `https://12stepsapp.luch-rehab.ru/api/v1/premium/webhook`
3. Событие: `payment.succeeded` (достаточно для MVP).
4. Return URL после оплаты: `https://12stepsapp.luch-rehab.ru/premium/return`  
   (страница открывает deep link `ru.na.steps12://premium/return`).
5. Для проверки используйте **тестовый** магазин/ключи; в админке отметьте «Тестовый режим».

### Админка

Раздел **ИИ** → сумма Premium, срок в днях после оплаты (по умолчанию 365), shopId / secretKey, лог последних платежей.

### Поток в приложении

Paywall → `POST /api/v1/premium/create-payment` → браузер ЮKassa (СБП/карта) → webhook выдаёт entitlement по `device_id` → приложение опрашивает `GET|POST /api/v1/premium/status` и синхронизирует локальный Premium.

Повторный webhook по тому же `payment_id` идемпотентен. Без Premium ИИ = `deepseek-v4-flash`, с Premium = `deepseek-v4-pro`.

Чек НПД: вручную в «Мой налог» или авточеки ЮKassa — не блокер MVP.

### Деплой сервера

```powershell
python server/deploy_ftp.py
```

Перед загрузкой на FTP автоматически повышается версия релиза (см. раздел **Версии и история изменений** ниже).

**Важно для агента / Cursor:** не запускать деплой сервера и установку на телефон, пока пользователь явно не попросит. Сборка debug APK и выкладка на Google Drive — часть `agent_release.py`, её делать нужно.

## Версии и история изменений

### Файлы

| Файл | Назначение |
|------|------------|
| `version.properties` | Единый источник версии: `VERSION_CODE` (целое для Android) и `VERSION_NAME` (строка, напр. `1.0.3`) |
| `app/build.gradle.kts` | Читает `version.properties`, прокидывает в APK и `BuildConfig` |
| `changelog.json` | История релизов в JSON (источник для приложения) |
| `CHANGELOG.md` | Тот же changelog в Markdown (для людей и git) |
| `app/src/main/assets/changelog.json` | Копия для экрана «Версия» в приложении |
| `tools/bump_version.py` | Повышение версии и синхронизация changelog |
| `tools/agent_release.py` | Bump + changelog + commit + push + сборка APK на Google Drive и ссылка в Google Doc |
| `tools/publish_apk.py` | Сборка debug APK → Google Drive → ссылка в [Google Doc](https://docs.google.com/document/d/1dcUoEwGAmScEghfdHBUAiblaz0sXCmmCRrFMzhCPP9E/edit?usp=sharing) |
| `.cursor/hooks.json` | Хук: после правок агента не даёт забыть релиз |
| `.cursor/rules/agent-release.mdc` | Правило для любого агента Cursor |

### После работы агента

После **каждого** изменения агентом (не только при деплое) версия поднимается сама, в changelog пишется описание, коммит уходит на GitHub, собирается debug APK и ссылка появляется в [Google Doc](https://docs.google.com/document/d/1dcUoEwGAmScEghfdHBUAiblaz0sXCmmCRrFMzhCPP9E/edit?usp=sharing):

```powershell
python tools/agent_release.py --notes "Что сделано;Второй пункт"
```

Это правило репозитория: агент делает bump/commit/push **и публикацию APK** в конце сессии, без отдельной просьбы. Деплой сервера и установку на телефон — только по явному запросу.

Если агент забыл, хук `stop` напомнит ему дописать релиз (не больше двух автоповторов). Временно отключить весь релиз: `$env:AGENT_RELEASE_SKIP = "1"`. Только без APK: `$env:AGENT_SKIP_APK = "1"` или `--skip-apk`.

### APK для тестеров (Google Drive + Google Doc)

Установочный файл кладётся на Google Диск, а в [документе с версиями](https://docs.google.com/document/d/1dcUoEwGAmScEghfdHBUAiblaz0sXCmmCRrFMzhCPP9E/edit?usp=sharing) сверху появляется блок: версия, описание правок и ссылка «Скачать APK». Это происходит **автоматически** в конце `agent_release.py`.

Первый раз на машине (откроется браузер Google — войдите в аккаунт, где лежит документ):

```powershell
python tools/publish_apk.py --auth
```

Скрипт сам скачает портативный `rclone` в `tools/.vendor/` (в git не попадает). Нужен доступ в интернет.

Повторить публикацию вручную (уже текущая версия, без нового bump):

```powershell
python tools/publish_apk.py
```

Готовый debug APK без пересборки: `python tools/publish_apk.py --skip-build`. Только переписать документ: `python tools/publish_apk.py --doc-only`.

### Нумерация

- **`VERSION_CODE`** — +1 при каждом релизе (нужен Android для обновления APK).
- **`VERSION_NAME`** — semver `MAJOR.MINOR.PATCH`, при bump автоматически +1 к **patch** (напр. `1.0.3` → `1.0.4`).

Major/minor вручную правятся в `version.properties`, если нужен скачок версии.

### Типичный релиз

1. Описать изменения и задеплоить сервер (версия поднимется сама):

```powershell
$env:DEPLOY_NOTES = "Экран версии;Исправлен paywall"
python server/deploy_ftp.py
```

2. Собрать APK с новой версией (см. **Обновление на телефоне**).
3. Установить на телефон.

Без `DEPLOY_NOTES` в changelog попадёт строка «Обновление сервера и приложения».

### Команды

**Автоматически после работы агента** — `tools/agent_release.py` (хук + правило Cursor): bump, GitHub, сборка APK, Google Drive, Google Doc.

**Автоматически при деплое** — `deploy_ftp.py` вызывает `bump_version.py`. Если версию уже поднял агент и нужен только FTP, задайте `$env:DEPLOY_SKIP_BUMP = "1"`.

**Вручную поднять версию** (без деплоя):

```powershell
python tools/bump_version.py --notes "Пункт 1;Пункт 2"
```

**Только скопировать changelog в assets** (если правили `changelog.json` руками):

```powershell
python tools/bump_version.py --sync-only
```

**Деплой без повышения версии:**

```powershell
$env:DEPLOY_SKIP_BUMP = "1"
python server/deploy_ftp.py
```

### Формат `changelog.json`

```json
{
  "releases": [
    {
      "version": "1.0.3",
      "versionCode": 4,
      "date": "2026-08-27",
      "items": ["Первый пункт", "Второй пункт"]
    }
  ]
}
```

Новые релизы добавляются **в начало** массива `releases`. После правки файла — `python tools/bump_version.py --sync-only` и пересборка APK.

### В приложении

**Настройки → Общие → «Версия и изменения»** — текущая версия и список релизов с описанием. Текущий релиз помечен как «установлена».

## Как открыть и собрать

1. Установите Android Studio.
2. File → Open → эта папка (`sites/12steps`).
3. Заполните `local.properties` (SDK + ключи при необходимости).
4. Run ▶

`applicationId`: `ru.na.steps12` — можно ставить рядом с «4 шаг · Обиды».

## Обновление на телефоне

**Не ставить APK на телефон без явного запроса пользователя.** Сборка для Google Drive идёт сама в `agent_release.py`.

На Xiaomi USB-установка из Android Studio часто блокируется. Рабочий путь — собрать debug APK и поставить через `adb push` + `pm install`.

В PowerShell из корня проекта:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat assembleDebug

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = "D:\sites\12steps\app\build\outputs\apk\debug\app-debug.apk"
& $adb push $apk /data/local/tmp/app-debug.apk
& $adb shell pm install -r -t /data/local/tmp/app-debug.apk
& $adb shell am start -n ru.na.steps12/ru.na.step4.obidy.MainActivity
```

Если `adb devices` показывает `offline` — `adb kill-server`, затем `adb start-server` и повторить установку.

После bump версии нужна **новая сборка**, иначе на телефоне останется старый номер версии и changelog.

Приложение не заменяет спонсора, группу и литературу АН. Электронный психолог — инструмент саморефлексии, не терапия и не медицина.

Оплата Premium — через ЮKassa (вне магазина приложений); в debug-сборке на экране Premium есть тестовая выдача на 30 дней.
