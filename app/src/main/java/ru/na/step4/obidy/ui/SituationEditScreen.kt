package ru.na.step4.obidy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.QuestionFocus
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.journal.EmotionCatalog
import ru.na.step4.obidy.data.journal.JournalFieldKind
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.data.notes.NoteMode
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.FieldBlock
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.ProgressBar
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.components.navigationBarsPaddingIfImeHidden
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.journal.JournalCard
import ru.na.step4.obidy.ui.journal.WordPickerScreen
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.SpeakableText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SituationEditScreen(
    viewModel: SituationEditViewModel,
    onBack: () -> Unit,
    onOpenList: () -> Unit,
    onAssistantFocus: (situationId: Long, focusKey: String) -> Unit,
    onPro: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var pickingFeelings by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val feltSelected = remember(state.iFelt) { EmotionCatalog.selectedWords(state.iFelt) }

    fun openAssist(focusKey: String) {
        viewModel.saveThen { situationId ->
            onAssistantFocus(situationId, focusKey)
        }
    }

    BackHandler(enabled = pickingFeelings) { pickingFeelings = false }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Sand,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { Ru.addSituation }, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = { viewModel.save(onBack) }) },
                actions = {
                    IconButton(onClick = { viewModel.save(onOpenList) }) {
                        Icon(Icons.Outlined.ViewList, Ru.resentments, tint = Forest)
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Outlined.Delete, Ru.delete, tint = Danger)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Sand.copy(alpha = 0.96f))
                    .imePadding()
                    .navigationBarsPaddingIfImeHidden()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { viewModel.save(onBack) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(Ru.save) }
            }
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        ProgressBar(state.progress, Situation.TOTAL_STEPS)
                    }
                    NoteView(NoteIds.INVENTORY_SITUATION, InventoryStructure.SITUATION_SECTION_HINT, compact = true)
                }

                if (state.canUseAi && !state.isAdmin && state.remainingAi < Int.MAX_VALUE) {
                    val used = JournalPrefs.DAILY_LIMIT - state.remainingAi
                    Text(
                        JournalRu.remainingAi.format(used.coerceAtLeast(0)),
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                JournalButton(
                    InventoryStructure.workThrough,
                    onClick = { viewModel.requestWorkThrough() },
                    filled = true
                )
                if (!state.isPro && !state.isAdmin) {
                    JournalButton(JournalRu.proNeededTitle, onClick = onPro)
                }
                Text(
                    InventoryStructure.workThroughHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.aiLoading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Forest)
                            Text(JournalRu.aiLoading, color = Forest, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                if (state.isAdmin && state.aiPrompt.isNotBlank()) {
                    ru.na.step4.obidy.ui.components.AdminPromptBlock(state.aiPrompt)
                }
                state.aiNotice?.let { notice ->
                    Text(notice, color = Forest, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.fullAnalysis.isNotBlank()) {
                    Text(
                        InventoryStructure.workThroughFullTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Forest
                    )
                    JournalCard {
                        SpeakableText(state.fullAnalysis) {
                            Text(
                                state.fullAnalysis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    JournalButton(InventoryStructure.dismissAnalysis, viewModel::dismissAnalysis)
                }

                FieldBlock(
                    "",
                    Ru.situationTitle,
                    Ru.situationTitleHint,
                    state.title,
                    viewModel::updateTitle,
                    1,
                    onAssistantClick = { openAssist(QuestionFocus.TITLE) }
                )
                NoteView(
                    NoteIds.INVENTORY_POINT_B,
                    InventoryStructure.POINT_B_BODY,
                    InventoryStructure.POINT_B,
                    defaultMode = NoteMode.COLLAPSED
                )
                FieldBlock(
                    "",
                    InventoryStructure.WHAT_TITLE,
                    InventoryStructure.WHAT_HINT,
                    state.whatHappened,
                    viewModel::updateWhatHappened,
                    onAssistantClick = { openAssist(QuestionFocus.WHAT) },
                    noteId = NoteIds.INVENTORY_WHAT,
                    aiInsight = state.insightFor(QuestionFocus.WHAT),
                    onInsertAi = { viewModel.applyInsight(QuestionFocus.WHAT) }
                )
                FieldBlock(
                    "",
                    InventoryStructure.FELT_TITLE,
                    InventoryStructure.FELT_HINT,
                    state.iFelt,
                    viewModel::updateIFelt,
                    onAssistantClick = { openAssist(QuestionFocus.FELT) },
                    onPickFromTable = { pickingFeelings = true },
                    pickFromTableCd = InventoryStructure.feelingsTable,
                    noteId = NoteIds.INVENTORY_FELT,
                    aiInsight = state.insightFor(QuestionFocus.FELT),
                    onInsertAi = { viewModel.applyInsight(QuestionFocus.FELT) }
                )
                FieldBlock(
                    "",
                    InventoryStructure.DID_TITLE,
                    InventoryStructure.DID_HINT,
                    state.iDid,
                    viewModel::updateIDid,
                    onAssistantClick = { openAssist(QuestionFocus.DID) },
                    noteId = NoteIds.INVENTORY_DID,
                    aiInsight = state.insightFor(QuestionFocus.DID),
                    onInsertAi = { viewModel.applyInsight(QuestionFocus.DID) }
                )
                InventoryStructure.questionsOf(1, 4).forEach { question ->
                    val key = QuestionFocus.q(question.number)
                    FieldBlock(
                        "",
                        question.title,
                        question.hint,
                        state.answers[question.number].orEmpty(),
                        { viewModel.updateAnswer(question.number, it) },
                        onAssistantClick = { openAssist(key) },
                        noteId = NoteIds.inventoryQuestion(question.number),
                        aiInsight = state.insightFor(key),
                        onInsertAi = { viewModel.applyInsight(key) }
                    )
                }

                NoteView(
                    NoteIds.INVENTORY_POINT_V,
                    InventoryStructure.POINT_V_BODY,
                    InventoryStructure.POINT_V,
                    defaultMode = NoteMode.COLLAPSED
                )
                InventoryStructure.questionsOf(5, 13).forEach { question ->
                    val key = QuestionFocus.q(question.number)
                    FieldBlock(
                        "",
                        question.title,
                        question.hint,
                        state.answers[question.number].orEmpty(),
                        { viewModel.updateAnswer(question.number, it) },
                        onAssistantClick = { openAssist(key) },
                        noteId = NoteIds.inventoryQuestion(question.number),
                        aiInsight = state.insightFor(key),
                        onInsertAi = { viewModel.applyInsight(key) }
                    )
                }

                NoteView(
                    NoteIds.INVENTORY_POINT_G,
                    InventoryStructure.POINT_G_BODY,
                    InventoryStructure.POINT_G,
                    defaultMode = NoteMode.COLLAPSED
                )
            }
        }
    }

    if (pickingFeelings) {
        WordPickerScreen(
            title = InventoryStructure.feelingsTable,
            kind = JournalFieldKind.FEELINGS,
            selected = feltSelected,
            onToggle = viewModel::toggleFeltWord,
            onBack = { pickingFeelings = false }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(Ru.deleteSituationTitle) },
            text = { Text(Ru.deleteSituationBody) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onBack) }) {
                    Text(Ru.delete, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(Ru.cancel) }
            }
        )
    }
    } // Box
}
