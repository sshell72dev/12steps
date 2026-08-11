package ru.na.step4.obidy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.SituationType
import ru.na.step4.obidy.data.SituationWithTypes
import ru.na.step4.obidy.data.TypeWithSituations
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.FieldBlock
import ru.na.step4.obidy.ui.components.HintIcon
import ru.na.step4.obidy.ui.components.ProgressBar
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
    var typesExpanded by remember { mutableStateOf(false) }

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
                    onClick = { viewModel.save { onBack() } },
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
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        ProgressBar(state.progress, state.totalSteps)
                    }
                    HintIcon(Ru.hintWork)
                }

                Text(InventoryStructure.POINT_A, style = MaterialTheme.typography.labelLarge, color = Amber)
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
                    minLines = 2
                )

                CollapsibleTypesSection(
                    expanded = typesExpanded,
                    onToggle = { typesExpanded = !typesExpanded },
                    types = state.tree.map { it.type },
                    typeCatalog = state.typeCatalog,
                    onPickType = viewModel::addType,
                    onDeleteType = { typeToDelete = it }
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
                    HintIcon(InventoryStructure.SITUATION_SECTION_HINT)
                }
                OutlinedTextField(
                    value = state.quickSituation,
                    onValueChange = viewModel::updateQuickSituation,
                    modifier = Modifier.fillMaxWidth(),
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
                        color = Forest
                    )
                    if (!expanded && selectedName.isNotBlank()) {
                        Text(
                            text = selectedName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Forest
                )
            }
            HintIcon(Ru.categoryHint)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsibleTypesSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    types: List<SituationType>,
    typeCatalog: List<String>,
    onPickType: (String) -> Unit,
    onDeleteType: (SituationType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = InventoryStructure.TYPE_SECTION,
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest
                    )
                    if (!expanded && types.isNotEmpty()) {
                        Text(
                            text = types.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Forest
                )
            }
            HintIcon(InventoryStructure.TYPE_SECTION_HINT)
        }
        if (expanded) {
            TypeAutocompleteField(
                catalog = typeCatalog,
                onPick = onPickType,
                catalogHint = Ru.typeCatalogHint
            )
            if (types.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { type ->
                        AssistChip(
                            onClick = { onDeleteType(type) },
                            label = { Text(type.name) },
                            trailingIcon = {
                                Icon(Icons.Outlined.Delete, null, tint = Danger)
                            }
                        )
                    }
                }
            }
        }
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
        modifier = Modifier
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
                modifier = Modifier.weight(1f)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onOpenSituation(situation.id) })
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(onClick = onAddSituation) { Text(Ru.addSituation, color = Forest) }
    }
}
