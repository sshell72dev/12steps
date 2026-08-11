# Vapi-консультант для «4 шаг · Обиды»

## Заполненный бриф

| Поле | Значение |
|------|----------|
| APP_NAME | 4 шаг · Обиды |
| GOAL | Помочь заполнить инвентарь обид по пунктам А–В и 13 вопросам; не заменять спонсора |
| LANG | ru |
| VALUE_PROP | Локальный инвентарь: категории (люди/учреждения/концепции), А→Б→В, прогресс, без облака |
| ICP | Участник АН, 4 шаг со спонсором; отсеять: кризис (к людям), «замени спонсора», мед./юр. советы |
| ALLOWED_FACTS | А: кому/чему; Б: что произошло / я чувствовал / я делал + вопросы 1–4; В: вопросы 5–13; данные на устройстве |
| FORBIDDEN | Обещать исцеление/трезвость; «готово без спонсора»; выдуманные цитаты |
| FUNNEL_STEPS | intent → A target → B what/felt/did → q1–q13 → close |
| TONE | «вы», спокойно; 1–2 предложения, один вопрос |

Источник структуры: стримы 48–62 и «13 вопросов к обидам».

---

## A) System prompt

См. `AssistantBrief.systemPromptTemplate()` — ephemeral assistant в приложении.

Переменные: `current_time_iso`, `channel`, `funnel_summary`, `dialog_history`, `funnel_step`, `category_names`, `resentment_context`, `inventory_total`, `inventory_done`.

---

## Редактирование

Промпт и first message: `app/.../assistant/AssistantBrief.kt`  
Локальная текстовая воронка: `LocalFunnel.kt`  
Ключ: `VAPI_PUBLIC_KEY` в `local.properties`
