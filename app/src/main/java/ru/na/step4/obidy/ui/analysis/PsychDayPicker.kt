package ru.na.step4.obidy.ui.analysis

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.data.psych.PsychLogic
import ru.na.step4.obidy.data.psych.PsychQa
import ru.na.step4.obidy.data.psych.PsychSituation
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.SandDeep

data class PsychDayEntry(
    val id: Long,
    val timeLabel: String,
    val situationText: String,
    val answers: List<PsychQa>
)

@Composable
fun PsychDayPickerIcon(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    IconButton(
        onClick = { open = true },
        modifier = modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.MenuBook,
            contentDescription = Ru.analysisPsychPickCd,
            tint = Moss.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp)
        )
    }
    if (open) {
        PsychDayPickerDialog(
            onDismiss = { open = false },
            onSelect = { text ->
                onInsert(text)
                open = false
            }
        )
    }
}

@Composable
private fun PsychDayPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<PsychDayEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        entries = withContext(Dispatchers.IO) {
            loadTodayPsychEntries(context.applicationContext as Application)
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(Ru.analysisPsychPickTitle, color = Forest)
        },
        text = {
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Forest, modifier = Modifier.size(28.dp))
                    }
                }
                entries.isEmpty() -> {
                    Text(
                        Ru.analysisPsychPickEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            PsychDayEntryCard(entry = entry, onSelect = onSelect)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Ru.cancel, color = Forest)
            }
        },
        containerColor = SandDeep
    )
}

@Composable
private fun PsychDayEntryCard(
    entry: PsychDayEntry,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SandDeep.copy(alpha = 0.55f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            entry.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Amber
        )
        if (entry.situationText.isNotBlank()) {
            SelectableSnippet(
                label = Ru.analysisPsychPickSituation,
                preview = entry.situationText,
                onClick = { onSelect(entry.situationText) }
            )
        }
        entry.answers.forEachIndexed { index, qa ->
            val answer = qa.answer.trim()
            if (answer.isBlank()) return@forEachIndexed
            val label = qa.question.trim().ifBlank {
                Ru.analysisPsychPickAnswer.format(index + 1)
            }
            SelectableSnippet(
                label = label,
                preview = answer,
                onClick = { onSelect(answer) }
            )
        }
        val all = entryAllText(entry)
        if (all.isNotBlank() && (entry.answers.any { it.answer.isNotBlank() })) {
            Text(
                Ru.analysisPsychPickAll,
                style = MaterialTheme.typography.labelMedium,
                color = Forest,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(all) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SelectableSnippet(
    label: String,
    preview: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Forest,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun entryAllText(entry: PsychDayEntry): String = buildString {
    if (entry.situationText.isNotBlank()) append(entry.situationText.trim())
    entry.answers.forEach { qa ->
        val a = qa.answer.trim()
        if (a.isBlank()) return@forEach
        if (isNotEmpty()) append("\n\n")
        append(a)
    }
}.trim()

private suspend fun loadTodayPsychEntries(app: Application): List<PsychDayEntry> {
    val step4 = app as Step4App
    val offset = step4.psychSettings.utcOffsetMinutes
    val (from, to) = PsychLogic.dayRange(System.currentTimeMillis(), offset)
    val sits = step4.psychRepository.situationsInRange(from, to)
    return sits.mapNotNull { sit -> toEntry(sit, step4, offset) }
        .filter { it.situationText.isNotBlank() || it.answers.any { qa -> qa.answer.isNotBlank() } }
}

private suspend fun toEntry(
    sit: PsychSituation,
    app: Step4App,
    offset: Int
): PsychDayEntry {
    val session = app.psychRepository.sessionForSituation(sit.id)
    val answers = session?.let { app.psychRepository.qaFor(it.sessionUid) }.orEmpty()
    return PsychDayEntry(
        id = sit.id,
        timeLabel = PsychLogic.formatLocal(sit.createdAt, offset),
        situationText = sit.text.trim(),
        answers = answers
    )
}

fun mergePsychIntoDraft(current: String, addition: String): String {
    val text = addition.trim()
    if (text.isEmpty()) return current
    return if (current.isBlank()) text else current.trimEnd() + "\n\n" + text
}
