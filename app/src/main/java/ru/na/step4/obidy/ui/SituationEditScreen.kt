package ru.na.step4.obidy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.FieldBlock
import ru.na.step4.obidy.ui.components.HintIcon
import ru.na.step4.obidy.ui.components.ProgressBar
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SituationEditScreen(
    viewModel: SituationEditViewModel,
    onBack: () -> Unit,
    onAssistantFocus: (situationId: Long, focusKey: String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var showTypePick by remember { mutableStateOf(false) }

    fun openAssist(focusKey: String) {
        viewModel.saveThen { situationId ->
            onAssistantFocus(situationId, focusKey)
        }
    }

    Scaffold(
        containerColor = Sand,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { Ru.addSituation }, color = Forest) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, Ru.back, tint = Forest)
                    }
                },
                actions = {
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
                    .navigationBarsPadding()
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        ProgressBar(state.progress, Situation.TOTAL_STEPS)
                    }
                    HintIcon(InventoryStructure.SITUATION_SECTION_HINT)
                }

                Text(Ru.situationTypesLabel, style = MaterialTheme.typography.titleSmall, color = Forest)
                if (state.linkedTypes.isEmpty()) {
                    Text(Ru.untaggedSituation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.linkedTypes.forEach { type ->
                            AssistChip(onClick = { showTypePick = true }, label = { Text(type.name) })
                        }
                    }
                }
                TextButton(onClick = { showTypePick = true }) {
                    Text(Ru.suggestTypes, color = Forest)
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
                FieldBlock(
                    InventoryStructure.POINT_B,
                    InventoryStructure.WHAT_TITLE,
                    InventoryStructure.WHAT_HINT,
                    state.whatHappened,
                    viewModel::updateWhatHappened,
                    onAssistantClick = { openAssist(QuestionFocus.WHAT) }
                )
                FieldBlock(
                    InventoryStructure.POINT_B,
                    InventoryStructure.FELT_TITLE,
                    InventoryStructure.FELT_HINT,
                    state.iFelt,
                    viewModel::updateIFelt,
                    onAssistantClick = { openAssist(QuestionFocus.FELT) }
                )
                FieldBlock(
                    InventoryStructure.POINT_B,
                    InventoryStructure.DID_TITLE,
                    InventoryStructure.DID_HINT,
                    state.iDid,
                    viewModel::updateIDid,
                    onAssistantClick = { openAssist(QuestionFocus.DID) }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        InventoryStructure.Q_SECTION,
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest,
                        modifier = Modifier.weight(1f)
                    )
                    HintIcon(InventoryStructure.Q_SECTION_HINT)
                }
                InventoryStructure.questions.forEach { question ->
                    FieldBlock(
                        if (question.number <= 4) InventoryStructure.POINT_B else InventoryStructure.POINT_V,
                        question.title,
                        question.hint,
                        state.answers[question.number].orEmpty(),
                        { viewModel.updateAnswer(question.number, it) },
                        onAssistantClick = { openAssist(QuestionFocus.q(question.number)) }
                    )
                }
            }
        }
    }

    if (showTypePick) {
        TypePickDialog(
            situationText = state.suggestText,
            existingTypes = state.allTypes.ifEmpty { state.linkedTypes },
            initiallySelectedIds = state.linkedTypes.map { it.id }.toSet(),
            onDismiss = { showTypePick = false },
            onConfirm = { existing, proposed ->
                viewModel.applyTypes(existing, proposed)
                showTypePick = false
            }
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
}
