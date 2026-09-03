package ru.na.step4.obidy.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.journal.NodeType
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalHubScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onEntries: () -> Unit,
    onPersonality: () -> Unit,
    onAiHelp: () -> Unit,
    onAiAnalyze: (String) -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onSupport: () -> Unit,
    onResentments: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()
    val path = state.path
    val next = viewModel.nextPoint()
    val showResentments = viewModel.catalog.isResentmentPlace(path)
    val resentmentPlace = showResentments && path?.point == null
    val streakLabel = remember(streakDays) { viewModel.streakLabel(streakDays) }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(2500)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(JournalRu.hubEyebrow, style = MaterialTheme.typography.labelMedium, color = Amber)
                        Text(JournalRu.hubTitle, style = MaterialTheme.typography.headlineMedium, color = Forest)
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = JournalRu.settings, tint = Forest)
                    }
                },
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
                NoteView(NoteIds.JOURNAL_HUB_INTRO, JournalRu.hubIntro, JournalRu.hubTitle)
                if (streakLabel != null) {
                    Text(
                        streakLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Amber
                    )
                }
                JournalCard {
                    if (path == null) {
                        Text(JournalRu.noPlace, color = Forest)
                    } else {
                        Text(JournalRu.currentStep, style = MaterialTheme.typography.labelMedium, color = Amber)
                        Text(
                            if (resentmentPlace) path.step.shortTitle() else path.step.displayTitle(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Forest
                        )
                        if (path.chapter != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(JournalRu.currentChapter, style = MaterialTheme.typography.labelMedium, color = Amber)
                            Text(
                                if (resentmentPlace) path.chapter.shortTitle() else path.chapter.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Forest
                            )
                        }
                        if (!resentmentPlace && path.point != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(JournalRu.currentPoint, style = MaterialTheme.typography.labelMedium, color = Amber)
                            Text(
                                "${path.point.name} (${state.currentCount})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Forest
                            )
                        } else if (!resentmentPlace && path.current.type == NodeType.STEP) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${JournalRu.leafHint} (${state.currentCount})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (showResentments) {
                    JournalButton(JournalRu.openInventory, onResentments, filled = true)
                }
                if (!resentmentPlace) {
                    path?.current?.let { node ->
                        NoteView(NoteIds.journal(node.id), node.description, node.displayTitle())
                    }
                    if (path?.current?.type == NodeType.POINT) {
                        JournalButton(JournalRu.aiHelp, onAiHelp)
                    }
                    if (state.writable) {
                        JournalEntryComposer(
                            state = state,
                            viewModel = viewModel
                        ) {
                            if (state.lastSaved != null) {
                                JournalButton(JournalRu.viewEntries, onEntries)
                                JournalButton(JournalRu.aiAnalyze, {
                                    state.lastSaved?.id?.let(onAiAnalyze)
                                })
                                JournalButton(JournalRu.editThis, {
                                    state.lastSaved?.let(viewModel::startEdit)
                                })
                            }
                        }
                    }
                }
                JournalButton(JournalRu.pickStep, onPick, filled = true)
                JournalButton(JournalRu.myEntries, onEntries)
                JournalButton(JournalRu.myPersonality, onPersonality)
                if (next != null) {
                    JournalButton("${JournalRu.nextPoint}: ${next.name.take(42)}", { viewModel.goNextPoint() })
                }
                JournalButton(JournalRu.settings, onSettings)
                JournalButton(JournalRu.help, onHelp)
                JournalButton(JournalRu.support, onSupport)
            }
        }
    }
}
