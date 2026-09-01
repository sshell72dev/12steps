package ru.na.step4.obidy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.InventoryFieldInsight
import ru.na.step4.obidy.data.InventoryInsightKind
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.notes.NoteMode
import ru.na.step4.obidy.data.notes.ResolvedNote
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.SpeakIconButton
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@Composable
fun AtmosphereBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Forest.copy(alpha = 0.08f),
                    Sand,
                    SandDeep.copy(alpha = 0.55f)
                )
            )
        )
    )
}

@Composable
fun HintIcon(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    var open by remember { mutableStateOf(false) }
    IconButton(
        onClick = { open = true },
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = Ru.hintCd,
            tint = Moss
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(Ru.hintTitle) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(Ru.hintClose)
                }
            }
        )
    }
}

@Composable
fun FieldBlock(
    step: String,
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 2,
    onAssistantClick: (() -> Unit)? = null,
    onPickFromTable: (() -> Unit)? = null,
    pickFromTableCd: String? = null,
    noteId: String? = null,
    aiInsight: InventoryFieldInsight? = null,
    onInsertAi: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (step.isNotBlank()) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall,
                color = Amber
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            when {
                aiInsight != null -> FieldHintWithAi(
                    title = title,
                    hint = hint,
                    noteId = noteId,
                    insight = aiInsight,
                    fieldFilled = value.isNotBlank(),
                    onInsert = onInsertAi
                )
                !noteId.isNullOrBlank() -> NoteView(noteId, hint, title, compact = true)
                hint.isNotBlank() -> HintIcon(hint)
            }
            if (onPickFromTable != null) {
                IconButton(onClick = onPickFromTable, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = pickFromTableCd ?: Ru.hintTitle,
                        tint = Forest
                    )
                }
            }
            if (onAssistantClick != null) {
                IconButton(onClick = onAssistantClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver,
                        contentDescription = Ru.assistantCd,
                        tint = Forest
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        VoiceOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Forest,
                unfocusedBorderColor = Moss.copy(alpha = 0.35f),
                focusedContainerColor = Sand.copy(alpha = 0.7f),
                unfocusedContainerColor = Sand.copy(alpha = 0.45f),
                cursorColor = Forest
            )
        )
    }
}

@Composable
private fun FieldHintWithAi(
    title: String,
    hint: String,
    noteId: String?,
    insight: InventoryFieldInsight,
    fieldFilled: Boolean,
    onInsert: (() -> Unit)?
) {
    val repo = LocalNotesRepository.current
    val emptyNotes = remember {
        kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, ru.na.step4.obidy.data.notes.NoteOverride>())
    }
    val notes by (repo?.notes ?: emptyNotes).collectAsState()
    val resolved = remember(noteId, hint, title, notes) {
        if (!noteId.isNullOrBlank() && repo != null) {
            repo.resolved(noteId, hint, title, NoteMode.POPUP)
        } else {
            ResolvedNote(noteId.orEmpty(), title, hint, NoteMode.POPUP, false)
        }
    }
    var open by remember { mutableStateOf(false) }
    val tint = if (insight.kind == InventoryInsightKind.BLIND_SPOT) Amber else Forest
    IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = Ru.hintCd, tint = tint)
    }
    if (open) {
        val insightLabel = when (insight.kind) {
            InventoryInsightKind.DRAFT -> InventoryStructure.insightDraftTitle
            InventoryInsightKind.BLIND_SPOT -> InventoryStructure.insightBlindTitle
        }
        val insertLabel = if (fieldFilled) InventoryStructure.appendDraft else InventoryStructure.insertDraft
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(resolved.title.ifBlank { title }.ifBlank { Ru.hintTitle }) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (resolved.text.isNotBlank()) {
                        Text(resolved.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        insightLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (insight.kind == InventoryInsightKind.BLIND_SPOT) Amber else Forest
                    )
                    Text(insight.text, style = MaterialTheme.typography.bodyMedium, color = Forest)
                }
            },
            confirmButton = {
                Row {
                    if (onInsert != null && insight.text.isNotBlank()) {
                        TextButton(onClick = {
                            onInsert()
                            open = false
                        }) { Text(insertLabel) }
                    }
                    TextButton(onClick = { open = false }) { Text(Ru.hintClose) }
                }
            },
            dismissButton = {
                val speak = buildString {
                    if (resolved.text.isNotBlank()) appendLine(resolved.text)
                    appendLine(insightLabel)
                    append(insight.text)
                }.trim()
                if (speak.isNotBlank()) SpeakIconButton(text = speak)
            }
        )
    }
}

@Composable
fun ProgressBar(current: Int, total: Int = Resentment.TOTAL_STEPS) {
    Column {
        Text(
            text = Ru.filled.format(current, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else current / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp)),
            color = Amber,
            trackColor = SandDeep,
            strokeCap = StrokeCap.Round
        )
    }
}

/** Info icon that opens the hint in a dialog. */
@Composable
fun SectionHint(text: String) {
    HintIcon(text)
}
