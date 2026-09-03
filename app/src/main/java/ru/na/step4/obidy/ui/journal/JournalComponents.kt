package ru.na.step4.obidy.ui.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.journal.EmotionCatalog
import ru.na.step4.obidy.data.journal.JournalFieldKind
import ru.na.step4.obidy.data.journal.JournalFieldSpec
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.SpeakableText
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@Composable
fun JournalButton(
    label: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
            shape = RoundedCornerShape(14.dp)
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(label, color = Forest) }
    }
}

@Composable
fun JournalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) { content() }
}

@Composable
fun DescriptionBlock(text: String, initiallyExpanded: Boolean = false) {
    if (text.isBlank()) return
    var expanded by remember(text) { mutableStateOf(initiallyExpanded || text.length < 420) }
    val shown = if (expanded) text else text.take(360).trimEnd() + "…"
    Column {
        SpeakableText(if (expanded) text else shown) {
            Text(
                shown,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (text.length >= 420) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) JournalRu.readLess else JournalRu.readMore, color = Forest)
            }
        }
    }
}

@Composable
fun CountPrefix(count: Int, onClick: (() -> Unit)? = null) {
    if (count <= 0) return
    Text(
        "($count)",
        style = MaterialTheme.typography.labelMedium,
        color = Amber,
        modifier = Modifier
            .padding(end = 6.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
            )
    )
}

@Composable
fun AccordionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    onCountClick: (() -> Unit)? = null,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (expanded) Forest.copy(alpha = 0.12f) else SandDeep.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CountPrefix(count, onClick = if (count > 0) onCountClick else null)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Forest)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (expanded) "▾" else "▸",
            color = Forest,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun LeafRow(
    title: String,
    count: Int,
    onClick: () -> Unit,
    onCountClick: (() -> Unit)? = null,
    highlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlighted) Forest.copy(alpha = 0.18f) else Sand.copy(alpha = 0.8f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CountPrefix(count, onClick = if (count > 0) onCountClick else null)
        Text(
            "•",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Forest
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = Forest,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JournalEntryComposer(
    state: JournalState,
    viewModel: JournalViewModel,
    afterSave: @Composable () -> Unit = {}
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<Pair<String, JournalFieldKind>?>(null) }
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoView),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.splitFields) {
                state.fields.forEach { field ->
                    JournalWriteField(
                        field = field,
                        value = state.fieldValues[field.id].orEmpty(),
                        onValueChange = { viewModel.setFieldValue(field.id, it) },
                        onPickWords = {
                            if (field.kind != JournalFieldKind.TEXT) {
                                picking = field.id to field.kind
                            }
                        },
                        onFocus = { scope.launch { bringIntoView.bringIntoView() } }
                    )
                }
            } else {
                VoiceOutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::setDraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusEvent {
                            if (it.isFocused) scope.launch { bringIntoView.bringIntoView() }
                        },
                    minLines = 5,
                    placeholder = { Text(JournalRu.writeHint) },
                    shape = RoundedCornerShape(12.dp),
                    colors = composerFieldColors()
                )
            }
            JournalButton(
                if (state.editingId != null) Ru.save else JournalRu.saveEntry,
                viewModel::saveDraft,
                filled = true
            )
            if (!state.notice.isNullOrBlank()) {
                Text(state.notice.orEmpty(), color = Amber, style = MaterialTheme.typography.bodyMedium)
            }
            afterSave()
        }
        picking?.let { (fieldId, kind) ->
            Dialog(
                onDismissRequest = { picking = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(Modifier.fillMaxSize()) {
                    WordPickerScreen(
                        title = when (kind) {
                            JournalFieldKind.FEELINGS -> JournalRu.pickFeelings
                            JournalFieldKind.THOUGHTS -> JournalRu.pickThoughts
                            JournalFieldKind.TEXT -> ""
                        },
                        kind = kind,
                        selected = EmotionCatalog.selectedWords(
                            state.fieldValues[fieldId].orEmpty(),
                            kind
                        ),
                        onToggle = { viewModel.toggleFieldWord(fieldId, it) },
                        onBack = { picking = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalWriteField(
    field: JournalFieldSpec,
    value: String,
    onValueChange: (String) -> Unit,
    onPickWords: () -> Unit,
    onFocus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                field.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Forest,
                modifier = Modifier.weight(1f)
            )
            if (field.kind == JournalFieldKind.FEELINGS) {
                IconButton(onClick = onPickWords) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = JournalRu.pickFeelings,
                        tint = Forest
                    )
                }
            }
            if (field.kind == JournalFieldKind.THOUGHTS) {
                IconButton(onClick = onPickWords) {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = JournalRu.pickThoughts,
                        tint = Forest
                    )
                }
            }
        }
        VoiceOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusEvent { if (it.isFocused) onFocus() },
            minLines = if (field.kind == JournalFieldKind.TEXT) 3 else 2,
            placeholder = { Text(JournalRu.writeHint) },
            shape = RoundedCornerShape(12.dp),
            colors = composerFieldColors()
        )
    }
}

@Composable
fun JournalFieldsSettings(viewModel: JournalViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(JournalRu.fieldsHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            JournalButton(
                JournalRu.fieldsSplit,
                { viewModel.setSplitFields(true) },
                filled = state.splitFields,
                modifier = Modifier.weight(1f)
            )
            JournalButton(
                JournalRu.fieldsSingle,
                { viewModel.setSplitFields(false) },
                filled = !state.splitFields,
                modifier = Modifier.weight(1f)
            )
        }
        if (state.splitFields) {
            JournalButton(JournalRu.addField, { addOpen = true })
            state.fields.forEachIndexed { index, field ->
                JournalCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.moveField(field.id, -1) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = JournalRu.moveUp, tint = Forest)
                        }
                        IconButton(
                            onClick = { viewModel.moveField(field.id, 1) },
                            enabled = index < state.fields.lastIndex
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = JournalRu.moveDown, tint = Forest)
                        }
                        OutlinedTextField(
                            value = field.title,
                            onValueChange = { viewModel.renameField(field.id, it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Forest
                            ),
                            colors = composerFieldColors()
                        )
                        if (state.fields.size > 1) {
                            IconButton(onClick = { viewModel.removeField(field.id) }) {
                                Icon(Icons.Outlined.Close, contentDescription = JournalRu.removeField, tint = Forest)
                            }
                        }
                    }
                    Text(
                        when (field.kind) {
                            JournalFieldKind.FEELINGS -> JournalRu.fieldKindFeelings
                            JournalFieldKind.THOUGHTS -> JournalRu.fieldKindThoughts
                            JournalFieldKind.TEXT -> JournalRu.fieldKindText
                        },
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
    if (addOpen) {
        AddFieldDialog(
            onDismiss = { addOpen = false },
            onAdd = { title, kind ->
                viewModel.addField(title, kind)
                addOpen = false
            }
        )
    }
}

@Composable
private fun AddFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (String, JournalFieldKind) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(JournalFieldKind.TEXT) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(JournalRu.addField) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(JournalRu.addFieldTitle) },
                    singleLine = true
                )
                Text(JournalRu.addFieldKind, color = Forest, style = MaterialTheme.typography.labelMedium)
                JournalButton(
                    JournalRu.fieldKindText,
                    { kind = JournalFieldKind.TEXT },
                    filled = kind == JournalFieldKind.TEXT
                )
                JournalButton(
                    JournalRu.fieldKindThoughts,
                    { kind = JournalFieldKind.THOUGHTS },
                    filled = kind == JournalFieldKind.THOUGHTS
                )
                JournalButton(
                    JournalRu.fieldKindFeelings,
                    { kind = JournalFieldKind.FEELINGS },
                    filled = kind == JournalFieldKind.FEELINGS
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(title, kind) }) { Text(Ru.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Ru.cancel) }
        }
    )
}

@Composable
private fun composerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Forest,
    unfocusedBorderColor = Moss.copy(alpha = 0.35f),
    focusedContainerColor = Sand.copy(alpha = 0.7f),
    unfocusedContainerColor = Sand.copy(alpha = 0.45f),
    cursorColor = Forest
)

@Composable
fun JournalEntryBody(
    text: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: androidx.compose.ui.graphics.Color = Forest,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = remember(text) { journalEntryAnnotated(text) },
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}

fun journalEntryAnnotated(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        val lines = text.split('\n')
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val colon = trimmed.indexOf(':')
            val isTitle = colon > 0 &&
                colon == trimmed.lastIndex &&
                trimmed.length in 2..48
            if (isTitle) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(line)
                pop()
            } else {
                append(line)
            }
            if (index < lines.lastIndex) append('\n')
        }
    }
}

@Composable
fun AnimatedChildren(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible) {
        Column(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}
