package ru.na.step4.obidy.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.data.book.BookFix
import ru.na.step4.obidy.data.book.BookRu
import ru.na.step4.obidy.data.book.applyBookFixes
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.journal.JournalCard
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.SpeakIconButton
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

/** Проверка текста: исходный текст, найденные правки и отметки пользователя. */
data class BookFixSession(
    val source: String,
    val fixes: List<BookFix>,
    val marked: Set<Int> = fixes.indices.toSet()
) {
    val markedFixes: List<BookFix> get() = fixes.filterIndexed { index, _ -> index in marked }

    /** Текст, каким он станет после принятия отмеченных правок. */
    val preview: String get() = applyBookFixes(source, markedFixes)

    fun toggled(index: Int): BookFixSession =
        copy(marked = if (index in marked) marked - index else marked + index)
}

/**
 * Экран исправлений: сверху старый и исправленный текст, ниже — каждая правка
 * с отдельной галочкой, внизу общие кнопки принятия и отмены.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFixReview(
    session: BookFixSession,
    onToggle: (Int) -> Unit,
    onAcceptAll: () -> Unit,
    onAcceptMarked: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(BookRu.fixTitle, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onCancel) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VoiceOutlinedTextField(
                    value = session.source,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(BookRu.fixOriginal) },
                    minLines = 4,
                    maxLines = 10,
                    voiceEnabled = false,
                    speakEnabled = false,
                    trailingIcon = { SpeakIconButton(text = session.source) }
                )
                VoiceOutlinedTextField(
                    value = session.preview,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(BookRu.fixFixed) },
                    minLines = 4,
                    maxLines = 10,
                    voiceEnabled = false,
                    speakEnabled = false,
                    trailingIcon = { SpeakIconButton(text = session.preview) }
                )

                Text(
                    if (session.fixes.isEmpty()) {
                        BookRu.fixNothing
                    } else {
                        BookRu.fixCount.format(session.fixes.size) +
                            " · " + BookRu.fixMarked.format(session.marked.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Forest
                )

                session.fixes.forEachIndexed { index, fix ->
                    FixRow(
                        fix = fix,
                        checked = index in session.marked,
                        onCheckedChange = { onToggle(index) }
                    )
                }

                JournalButton(
                    label = BookRu.fixAcceptAll,
                    onClick = onAcceptAll,
                    filled = true,
                    enabled = session.fixes.isNotEmpty()
                )
                JournalButton(
                    label = BookRu.fixAcceptSelected,
                    onClick = onAcceptMarked,
                    enabled = session.marked.isNotEmpty()
                )
                JournalButton(label = BookRu.fixCancel, onClick = onCancel)
            }
        }
    }
}

@Composable
private fun FixRow(fix: BookFix, checked: Boolean, onCheckedChange: () -> Unit) {
    JournalCard(onClick = onCheckedChange) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.size(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${BookRu.fixWas}: ${fix.old}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${BookRu.fixBecame}: ${fix.new}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Forest
                )
                if (fix.reason.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        fix.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SpeakIconButton(text = fix.new)
        }
    }
}
