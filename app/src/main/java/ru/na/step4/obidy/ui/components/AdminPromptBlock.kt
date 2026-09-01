package ru.na.step4.obidy.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.data.i18n.I18n
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.SpeakIconButton

data class PromptOrigin(
    val name: String,
    val screen: String,
    val source: String,
    val route: String = ""
) {
    fun footer(): String = buildString {
        append("\n\n---\n")
        append("Это промпт: $name\n")
        append("Экран: $screen\n")
        if (route.isNotBlank()) append("Маршрут: $route\n")
        append("Откуда: $source")
    }

    companion object {
        fun journalHelp(entry: Boolean) = PromptOrigin(
            name = if (entry) "Помощь по записи дневника" else "Помощь по точке дневника",
            screen = "ИИ дневника",
            source = if (entry) {
                "сервер · role journal.help_entry · POST /api/v1/chat"
            } else {
                "сервер · role journal.help · POST /api/v1/chat"
            },
            route = if (entry) "journal/ai/help/{id}" else "journal/ai/help"
        )

        fun journalAnalyze(entry: Boolean) = PromptOrigin(
            name = if (entry) "Анализ одной записи дневника" else "Анализ записей по точке дневника",
            screen = "ИИ дневника",
            source = "сервер · role journal.analyze · POST /api/v1/chat",
            route = if (entry) "journal/ai/analyze/{id}" else "journal/ai/analyze"
        )

        fun inventory(full: Boolean) = PromptOrigin(
            name = if (full) "Полная проработка ситуации обиды" else "Проработка полей ситуации обиды",
            screen = "Ситуация обиды",
            source = if (full) {
                "сервер · role inventory.analyze · POST /api/v1/chat"
            } else {
                "сервер · role inventory.work · POST /api/v1/chat"
            },
            route = "situation/{id}"
        )

        fun analysis() = PromptOrigin(
            name = "Анализ самоанализа",
            screen = "Самоанализ",
            source = "сервер · role analysis.review · POST /api/v1/analyze",
            route = "analysis/session/{id}"
        )

        fun psych(kind: String) = PromptOrigin(
            name = when (kind) {
                "analyze" -> "Разбор ситуации"
                "recommend" -> "Рекомендации по ситуации"
                "questions", "questions_retry" -> "Вопросы проработки ситуации"
                "questions_next" -> "Следующий вопрос проработки"
                "dialogue_question" -> "Живой вопрос по ситуации"
                "assistant" -> "Ассистент ИИ по ситуации"
                "tts_understanding" -> "Пересказ понимания ситуации"
                "reminder_outreach" -> "Напоминание-приглашение"
                else -> "Запрос ИИ ($kind)"
            },
            screen = "Электронный психолог",
            source = "сервер · role psych.$kind · POST /api/v1/psych",
            route = "psych"
        )
    }
}

object AdminAiPrompt {
    private const val ORIGIN_MARK = "\nЭто промпт: "

    fun format(system: String, user: String, origin: PromptOrigin? = null): String =
        withOrigin("SYSTEM:\n$system\n\nUSER:\n$user", origin)

    fun withOrigin(prompt: String, origin: PromptOrigin?): String {
        val body = prompt.trimEnd()
        if (body.isBlank() || origin == null) return body
        if (body.contains("$ORIGIN_MARK${origin.name}\n")) return body
        return body + origin.footer()
    }

    fun speakable(prompt: String): String {
        val idx = prompt.lastIndexOf("\n\n---$ORIGIN_MARK")
        return if (idx >= 0) prompt.substring(0, idx).trimEnd() else prompt
    }
}

@Composable
fun AdminPromptBlock(
    prompt: String,
    modifier: Modifier = Modifier,
    origin: PromptOrigin? = null,
    title: String = I18n.t("psych.adminPrompt", "Промпт к ИИ")
) {
    if (prompt.isBlank()) return
    val display = remember(prompt, origin) { AdminAiPrompt.withOrigin(prompt, origin) }
    val speak = remember(display) { AdminAiPrompt.speakable(display) }
    val context = LocalContext.current
    val copyCd = I18n.t("psych.copyPrompt", "Копировать промпт")
    val copied = I18n.t("psych.promptCopied", "Промпт скопирован")
    var open by remember(display) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SandDeep.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .clickable { open = !open }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Amber
            )
            SpeakIconButton(text = speak, tint = Amber)
            IconButton(
                onClick = { copyPrompt(context, display, copied) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = copyCd,
                    tint = Amber,
                    modifier = Modifier.size(24.dp)
                )
            }
            Icon(
                imageVector = if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier
                    .clickable { open = !open }
                    .padding(8.dp)
            )
        }
        if (open) {
            Text(
                display,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Moss
            )
        }
    }
}

private fun copyPrompt(context: Context, text: String, copied: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("prompt", text))
    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
}
