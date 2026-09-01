package ru.na.step4.obidy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.data.notes.NoteMode
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.components.navigationBarsPaddingIfImeHidden
import ru.na.step4.obidy.ui.components.FieldBlock
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.ProgressBar
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    viewModel: EditViewModel,
    onBack: () -> Unit,
    onOpenList: () -> Unit,
    onOpenSituation: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var situationToDelete by remember { mutableStateOf<Situation?>(null) }

    Scaffold(
        containerColor = Sand,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                navigationIcon = { AppNavIcon(onBack = { viewModel.save { onBack() } }) },
                actions = {
                    IconButton(onClick = { viewModel.save { onOpenList() } }) {
                        Icon(Icons.Outlined.ViewList, Ru.resentments, tint = Forest)
                    }
                    if (state.id > 0) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Outlined.Delete, Ru.delete, tint = Danger)
                        }
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
                    onClick = { viewModel.save { onBack() } },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteView(
                    NoteIds.INVENTORY_WORK,
                    InventoryStructure.INTRO,
                    InventoryStructure.INTRO_TITLE,
                    defaultMode = NoteMode.COLLAPSED
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        ProgressBar(state.progress, state.totalSteps)
                    }
                }

                NoteView(
                    NoteIds.INVENTORY_POINT_A,
                    InventoryStructure.POINT_A_BODY,
                    InventoryStructure.POINT_A,
                    defaultMode = NoteMode.COLLAPSED
                )
                CategoryPicker(
                    categories = state.categories,
                    selectedId = state.categoryId,
                    initiallyExpanded = state.loaded && state.target.isBlank(),
                    ready = state.loaded,
                    onSelect = viewModel::setCategory
                )
                TargetAutocompleteField(
                    value = state.target,
                    onValueChange = viewModel::updateTarget,
                    knownTargets = state.knownTargets,
                    label = InventoryStructure.TARGET_TITLE,
                    hint = InventoryStructure.TARGET_HINT,
                    catalogHint = Ru.targetCatalogHint,
                    noteId = NoteIds.INVENTORY_TARGET,
                    minLines = 2
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        InventoryStructure.SITUATION_SECTION,
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest,
                        modifier = Modifier.weight(1f)
                    )
                    NoteView(NoteIds.INVENTORY_SITUATION, InventoryStructure.SITUATION_SECTION_HINT, compact = true)
                }
                Button(
                    onClick = { viewModel.addBlankSituation(onOpenSituation) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Sand)
                    Text(
                        Ru.addSituation,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (state.situations.isEmpty()) {
                    Text(Ru.noSituationsYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.situations.forEachIndexed { index, situation ->
                        SituationCard(
                            index = index + 1,
                            situation = situation,
                            onClick = { onOpenSituation(situation.id) },
                            onDelete = { situationToDelete = situation }
                        )
                    }
                }

                FieldBlock("", Ru.notes, "", state.notes, viewModel::updateNotes, 3)
                Row(
                    Modifier.fillMaxWidth(),
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
            }
        }
    }

    situationToDelete?.let { situation ->
        AlertDialog(
            onDismissRequest = { situationToDelete = null },
            title = { Text(Ru.deleteSituationTitle) },
            text = { Text(Ru.deleteSituationBody) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSituation(situation)
                    situationToDelete = null
                }) { Text(Ru.delete, color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { situationToDelete = null }) { Text(Ru.cancel) }
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

@Composable
private fun SituationCard(
    index: Int,
    situation: Situation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val secondary = listOf(situation.whatHappened, situation.iFelt, situation.iDid)
        .firstOrNull { it.isNotBlank() && it != situation.preview }
        .orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SandDeep.copy(alpha = 0.55f))
            .border(1.dp, Forest.copy(alpha = 0.14f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Ru.situationCardLabel.format(index),
                    style = MaterialTheme.typography.labelMedium,
                    color = Amber
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = situation.preview,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Forest,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (secondary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, Ru.delete, tint = Danger)
            }
        }
        ProgressBar(situation.progressSteps, Situation.TOTAL_STEPS)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long?,
    initiallyExpanded: Boolean,
    ready: Boolean,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(ready, initiallyExpanded) {
        if (ready && !initialized) {
            expanded = initiallyExpanded
            initialized = true
        }
    }

    val selectedName = categories.firstOrNull { it.id == selectedId }?.name
        ?: if (selectedId == null && ready) Ru.uncategorized else ""

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Forest.copy(alpha = 0.18f),
        selectedLabelColor = Forest,
        containerColor = Sand,
        labelColor = Forest
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = { expanded = !expanded })
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Ru.categoryLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!expanded && selectedName.isNotBlank()) {
                        Text(
                            text = selectedName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Forest
                )
            }
            NoteView(NoteIds.INVENTORY_CATEGORY, Ru.categoryHint, compact = true)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedId == category.id,
                        onClick = {
                            onSelect(category.id)
                            expanded = false
                        },
                        label = { Text(category.name) },
                        colors = chipColors
                    )
                }
                FilterChip(
                    selected = selectedId == null,
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                    label = { Text(Ru.uncategorized) },
                    colors = chipColors
                )
            }
        }
    }
}
