# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def w(rel, content):
    content = content.replace("UI_MODIFIER", MOD)
    (ROOT / rel).write_text(content, encoding="utf-8", newline="\n")
    print("wrote", rel)


# Fix SituationEditViewModel imports
sev = ROOT / "ui/SituationEditViewModel.kt"
t = sev.read_text(encoding="utf-8")
t = t.replace(
    "import kotlinx.coroutines.flow.combine\n",
    "import kotlinx.coroutines.flow.combine\nimport kotlinx.coroutines.flow.first\n",
)
t = t.replace(
    """            repository.getSituation(situationId)?.let { item ->
                val all = repository.observeTypes(item.resentmentId)
                // load once
                val types = kotlinx.coroutines.flow.first(repository.observeTypes(item.resentmentId))
                form.value = item.toUiState(types)
            }""",
    """            repository.getSituation(situationId)?.let { item ->
                val types = repository.observeTypes(item.resentmentId).first()
                form.value = item.toUiState(types)
            }""",
)
t = t.replace(
    "val types = kotlinx.coroutines.flow.first(repository.observeTypes(rid))",
    "val types = repository.observeTypes(rid).first()",
)
sev.write_text(t, encoding="utf-8", newline="\n")
print("fixed SituationEditViewModel")

w(
    "ui/EditScreen.kt",
    r'''package ru.na.step4.obidy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.UI_MODIFIER
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.SituationType
import ru.na.step4.obidy.data.SituationWithTypes
import ru.na.step4.obidy.data.TypeWithSituations
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.FieldBlock
import ru.na.step4.obidy.ui.components.ProgressBar
import ru.na.step4.obidy.ui.components.SectionHint
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    viewModel: EditViewModel,
    onBack: () -> Unit,
    onOpenSituation: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var typeToDelete by remember { mutableStateOf<SituationType?>(null) }
    var typePick by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.target.isBlank()) Ru.newResentment else state.target,
                        style = MaterialTheme.typography.titleLarge,
                        color = Forest,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save { onBack() } }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, Ru.back, tint = Forest)
                    }
                },
                actions = {
                    if (state.id > 0) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Outlined.Delete, Ru.delete, tint = Danger)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(UI_MODIFIER.fillMaxSize().padding(padding)) {
            AtmosphereBackground(UI_MODIFIER.fillMaxSize())
            Column(
                UI_MODIFIER
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ProgressBar(state.progress, state.totalSteps)
                SectionHint(Ru.hintWork)

                Text(InventoryStructure.POINT_A, style = MaterialTheme.typography.labelLarge, color = Amber)
                FieldBlock(
                    "",
                    InventoryStructure.TARGET_TITLE,
                    InventoryStructure.TARGET_HINT,
                    state.target,
                    viewModel::updateTarget,
                    2
                )

                Text(InventoryStructure.TYPE_SECTION, style = MaterialTheme.typography.titleMedium, color = Forest)
                SectionHint(InventoryStructure.TYPE_SECTION_HINT)
                TypeAutocompleteField(
                    catalog = state.typeCatalog,
                    onPick = viewModel::addType
                )
                if (state.tree.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tree.forEach { branch ->
                            AssistChip(
                                onClick = { typeToDelete = branch.type },
                                label = { Text(branch.type.name) },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Delete, null, tint = Danger)
                                }
                            )
                        }
                    }
                }

                Text(InventoryStructure.SITUATION_SECTION, style = MaterialTheme.typography.titleMedium, color = Forest)
                SectionHint(InventoryStructure.SITUATION_SECTION_HINT)
                OutlinedTextField(
                    value = state.quickSituation,
                    onValueChange = viewModel::updateQuickSituation,
                    modifier = UI_MODIFIER.fillMaxWidth(),
                    label = { Text(Ru.quickSituationHint) },
                    minLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.addSituationFromQuick { id, text ->
                                typePick = id to text
                            }
                        },
                        enabled = state.quickSituation.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(Ru.suggestTypes) }
                    TextButton(onClick = { viewModel.addBlankSituation(onOpenSituation) }) {
                        Icon(Icons.Outlined.Add, null, tint = Forest)
                        Text(Ru.addSituation, color = Forest)
                    }
                }

                if (state.situations.isEmpty()) {
                    Text(Ru.noSituationsYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.situations.forEach { row ->
                        SituationWithTypesRow(
                            row = row,
                            onClick = { onOpenSituation(row.situation.id) },
                            onSuggest = {
                                typePick = row.situation.id to listOf(
                                    row.situation.title,
                                    row.situation.whatHappened
                                ).filter { it.isNotBlank() }.joinToString(" ")
                            }
                        )
                    }
                }

                state.tree.forEach { branch ->
                    TypeBranchBlock(
                        branch = branch,
                        onAddSituation = {
                            viewModel.addSituationUnderType(branch.type.id, onOpenSituation)
                        },
                        onOpenSituation = onOpenSituation,
                        onDeleteType = { typeToDelete = branch.type }
                    )
                }

                FieldBlock("", Ru.notes, "", state.notes, viewModel::updateNotes, 3)
                Row(
                    UI_MODIFIER.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(Ru.markDone, color = Forest)
                    Switch(
                        checked = state.isCompleted,
                        onCheckedChange = { viewModel.toggleCompleted() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Moss)
                    )
                }

                Button(
                    onClick = { viewModel.save { onBack() } },
                    modifier = UI_MODIFIER.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(Ru.save) }
                Spacer(UI_MODIFIER.height(24.dp))
            }
        }
    }

    typePick?.let { (situationId, text) ->
        TypePickDialog(
            situationText = text,
            existingTypes = state.tree.map { it.type },
            initiallySelectedIds = state.situations
                .firstOrNull { it.situation.id == situationId }
                ?.types?.map { it.id }?.toSet()
                .orEmpty(),
            onDismiss = { typePick = null },
            onConfirm = { existing, proposed ->
                viewModel.applyTypesForSituation(situationId, existing, proposed)
                typePick = null
            }
        )
    }

    typeToDelete?.let { type ->
        AlertDialog(
            onDismissRequest = { typeToDelete = null },
            title = { Text(Ru.deleteTypeTitle) },
            text = { Text(Ru.deleteTypeBody) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeType(type)
                    typeToDelete = null
                }) { Text(Ru.delete, color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { typeToDelete = null }) { Text(Ru.cancel) }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(Ru.deleteTitle) },
            text = { Text(Ru.deleteBody) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SituationWithTypesRow(
    row: SituationWithTypes,
    onClick: () -> Unit,
    onSuggest: () -> Unit
) {
    Column(
        modifier = UI_MODIFIER
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(row.situation.preview, style = MaterialTheme.typography.titleSmall, color = Forest)
        if (row.types.isEmpty()) {
            Text(Ru.untaggedSituation, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.types.forEach { type ->
                    AssistChip(onClick = onClick, label = { Text(type.name) })
                }
            }
        }
        ProgressBar(row.situation.progressSteps, Situation.TOTAL_STEPS)
        TextButton(onClick = onSuggest) { Text(Ru.suggestTypes, color = Forest) }
    }
}

@Composable
private fun TypeBranchBlock(
    branch: TypeWithSituations,
    onAddSituation: () -> Unit,
    onOpenSituation: (Long) -> Unit,
    onDeleteType: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                branch.type.name,
                style = MaterialTheme.typography.titleSmall,
                color = Forest,
                modifier = UI_MODIFIER.weight(1f)
            )
            IconButton(onClick = onAddSituation) {
                Icon(Icons.Outlined.Add, Ru.addSituation, tint = Forest)
            }
            IconButton(onClick = onDeleteType) {
                Icon(Icons.Outlined.Delete, Ru.delete, tint = Danger)
            }
        }
        branch.situations.forEach { situation ->
            Text(
                situation.preview,
                modifier = UI_MODIFIER
                    .fillMaxWidth()
                    .clickable { onOpenSituation(situation.id) }
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(onClick = onAddSituation) { Text(Ru.addSituation, color = Forest) }
    }
}
''',
)

print("EditScreen ok")
