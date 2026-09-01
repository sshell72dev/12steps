package ru.na.step4.obidy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.notes.NoteMode
import ru.na.step4.obidy.data.notes.NotesRepository
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.SpeakIconButton
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

val LocalNotesRepository = staticCompositionLocalOf<NotesRepository?> { null }

@Composable
fun NoteView(
    id: String,
    defaultText: String,
    title: String = Ru.hintTitle,
    modifier: Modifier = Modifier,
    defaultMode: NoteMode = NoteMode.POPUP,
    compact: Boolean = false
) {
    val repo = LocalNotesRepository.current
    val emptyNotes = remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, ru.na.step4.obidy.data.notes.NoteOverride>()) }
    val emptyAdmin = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val notes by (repo?.notes ?: emptyNotes).collectAsState()
    val isAdmin by (repo?.isAdmin ?: emptyAdmin).collectAsState()
    val resolved = remember(id, defaultText, title, defaultMode, notes) {
        repo?.resolved(id, defaultText, title, defaultMode) ?: ru.na.step4.obidy.data.notes.ResolvedNote(
            id, title, defaultText, defaultMode, false
        )
    }
    if (resolved.text.isBlank() && !isAdmin) return

    var popup by remember(id) { mutableStateOf(false) }
    var open by remember(id, resolved.mode) { mutableStateOf(resolved.mode == NoteMode.EXPANDED) }
    var editor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val heading = resolved.title.ifBlank { title }

    val asPopup = compact || resolved.mode == NoteMode.POPUP
    Column(
        modifier = modifier.then(
            if (asPopup) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()
        )
    ) {
        when {
            asPopup -> {
                if (!compact && resolved.showTitle && heading.isNotBlank()) {
                    NoteHeading(heading)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (resolved.text.isNotBlank()) {
                        IconButton(onClick = { popup = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Info, contentDescription = Ru.hintCd, tint = Moss)
                        }
                    }
                    if (isAdmin) Pencil { editor = true }
                }
            }
            else -> {
                AccordionNote(
                    title = heading,
                    text = resolved.text,
                    titleOutside = resolved.showTitle,
                    open = open,
                    onToggle = { open = !open },
                    isAdmin = isAdmin,
                    onEdit = { editor = true }
                )
            }
        }
    }

    if (popup && resolved.text.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { popup = false },
            title = { Text(resolved.title.ifBlank { Ru.hintTitle }) },
            text = {
                Text(
                    resolved.text,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { popup = false }) { Text(Ru.hintClose) }
            },
            dismissButton = {
                if (resolved.text.isNotBlank()) {
                    SpeakIconButton(text = resolved.text)
                }
            }
        )
    }

    if (editor && repo != null) {
        NoteEditorDialog(
            id = id,
            title = resolved.title.ifBlank { title },
            text = resolved.text,
            mode = resolved.mode,
            showTitle = resolved.showTitle,
            onDismiss = { editor = false },
            onSave = { newTitle, newText, newMode, newShowTitle ->
                scope.launch {
                    repo.save(id, newTitle, newText, newMode, newShowTitle)
                    editor = false
                    open = newMode == NoteMode.EXPANDED
                }
            }
        )
    }
}

@Composable
private fun AccordionNote(
    title: String,
    text: String,
    titleOutside: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    isAdmin: Boolean,
    onEdit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (titleOutside && title.isNotBlank()) {
            NoteHeading(title)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = text.isNotBlank() || title.isNotBlank()) { onToggle() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = Forest,
                modifier = Modifier.size(20.dp)
            )
            Text(
                if (titleOutside || title.isBlank()) {
                    if (open) JournalRu.readLess else JournalRu.readMore
                } else {
                    title
                },
                style = MaterialTheme.typography.titleSmall,
                color = Forest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            if (text.isNotBlank()) SpeakIconButton(text = text, tint = Forest)
            if (isAdmin) Pencil(onEdit)
        }
        if (open) {
            NoteBody(text)
        }
    }
}

@Composable
private fun NoteHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = Forest,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun NoteBody(text: String) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun Pencil(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Outlined.Edit, contentDescription = JournalRu.editNote, tint = Forest)
    }
}

@Composable
private fun NoteEditorDialog(
    id: String,
    title: String,
    text: String,
    mode: NoteMode,
    showTitle: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, NoteMode, Boolean) -> Unit
) {
    var titleDraft by remember(id) { mutableStateOf(title) }
    var textDraft by remember(id) { mutableStateOf(text) }
    var modeDraft by remember(id) { mutableStateOf(mode) }
    var showTitleDraft by remember(id) { mutableStateOf(showTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(JournalRu.editNote) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(id, style = MaterialTheme.typography.labelMedium, color = Moss)
                VoiceOutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(JournalRu.noteTitle) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTitleDraft = !showTitleDraft },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showTitleDraft,
                        onCheckedChange = { showTitleDraft = it },
                        colors = CheckboxDefaults.colors(checkedColor = Forest)
                    )
                    Text(
                        JournalRu.noteShowTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Forest
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip(JournalRu.notePopup, modeDraft == NoteMode.POPUP) { modeDraft = NoteMode.POPUP }
                    ModeChip(JournalRu.noteCollapsed, modeDraft == NoteMode.COLLAPSED) { modeDraft = NoteMode.COLLAPSED }
                    ModeChip(JournalRu.noteExpanded, modeDraft == NoteMode.EXPANDED) { modeDraft = NoteMode.EXPANDED }
                }
                VoiceOutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(JournalRu.noteText) },
                    minLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(titleDraft, textDraft, modeDraft, showTitleDraft) }) { Text(Ru.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Ru.cancel) }
        }
    )
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Forest,
            selectedLabelColor = Sand
        )
    )
}
