package ru.na.step4.obidy.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.journal.JournalEntry
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntriesScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAiAnalyze: (String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val allEntries = state.entries.sortedByDescending { it.createdAt }
    val currentId = state.path?.current?.id
    val currentEntries = currentId?.let { id ->
        allEntries.filter { it.nodeId == id }
    }.orEmpty()
    val otherEntries = if (currentId == null) allEntries
    else allEntries.filter { it.nodeId != currentId }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(JournalRu.myEntries, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    IconButton(onClick = { viewModel.exportToDownloads(context) }) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = JournalRu.exportJson,
                            tint = Forest
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "${JournalRu.allEntries} (${allEntries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest
                    )
                    if (!state.notice.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(state.notice.orEmpty(), color = Amber, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (allEntries.isEmpty()) {
                    item {
                        Text(JournalRu.noEntries, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (currentId == null) {
                    items(allEntries, key = { it.id }) { entry ->
                        EntryCard(entry, viewModel, onOpen)
                    }
                } else {
                    item {
                        Text(
                            "${state.path?.line().orEmpty()} (${currentEntries.size})",
                            color = Forest,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (currentEntries.isEmpty()) {
                        item {
                            Text(JournalRu.noEntriesHere, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(currentEntries, key = { it.id }) { entry ->
                            EntryCard(entry, viewModel, onOpen)
                        }
                    }
                    if (otherEntries.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(JournalRu.pickOtherRubrics, style = MaterialTheme.typography.titleMedium, color = Forest)
                        }
                        items(otherEntries, key = { "o-${it.id}" }) { entry ->
                            EntryCard(entry, viewModel, onOpen)
                        }
                    }
                }
                if (allEntries.isNotEmpty()) {
                    item {
                        JournalButton(JournalRu.exportJson, { viewModel.exportToDownloads(context) })
                    }
                }
                item { JournalButton(JournalRu.mainMenu, onBack, filled = true) }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: JournalEntry,
    viewModel: JournalViewModel,
    onOpen: (String) -> Unit
) {
    val path = viewModel.catalog.pathOf(entry.nodeId)
    JournalCard(onClick = { onOpen(entry.id) }) {
        Text(viewModel.formatDate(entry.createdAt), style = MaterialTheme.typography.labelMedium, color = Amber)
        Spacer(Modifier.height(4.dp))
        Text(path?.line().orEmpty(), style = MaterialTheme.typography.labelMedium, color = Forest)
        Spacer(Modifier.height(6.dp))
        Text(
            remember(entry.text) { journalEntryAnnotated(entry.text) },
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryScreen(
    viewModel: JournalViewModel,
    entryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAiAnalyze: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entry = state.entries.find { it.id == entryId }
    val path = entry?.let { viewModel.catalog.pathOf(it.nodeId) }
    var pendingDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(JournalRu.myEntries, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (entry == null) {
                    Text(JournalRu.noEntries, color = Forest)
                } else {
                    Text(path?.line().orEmpty(), color = Forest, style = MaterialTheme.typography.titleMedium)
                    Text(viewModel.formatDate(entry.createdAt), color = Amber)
                    JournalEntryBody(entry.text)
                    JournalButton(JournalRu.edit, onEdit, filled = true)
                    JournalButton(JournalRu.aiAnalyze, { onAiAnalyze(entry.id) })
                    JournalButton(Ru.delete, onClick = { pendingDelete = true })
                }
            }
        }
    }
    if (pendingDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text(JournalRu.deleteEntry) },
            text = { Text(JournalRu.deleteEntryBody) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry.id)
                    pendingDelete = false
                    onBack()
                }) { Text(Ru.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) { Text(Ru.cancel) }
            }
        )
    }
}
