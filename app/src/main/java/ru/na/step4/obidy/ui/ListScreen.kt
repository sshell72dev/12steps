package ru.na.step4.obidy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.ResentmentListItem
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.ProgressBar
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onOpen: (Long) -> Unit,
    onGuide: () -> Unit,
    onCategories: () -> Unit,
    onAssistant: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryNames = state.categories.associate { it.id to it.name }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Resentment?>(null) }
    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFromUri(context, uri)
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    Scaffold(
        containerColor = Sand,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Ru.step4,
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            Ru.resentments,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Forest
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            openDocument.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "text/*",
                                    "*/*"
                                )
                            )
                        }
                    ) {
                        Icon(
                            Icons.Outlined.FileUpload,
                            contentDescription = Ru.importCd,
                            tint = Forest
                        )
                    }
                    IconButton(onClick = onAssistant) {
                        Icon(
                            Icons.Outlined.RecordVoiceOver,
                            contentDescription = Ru.assistantCd,
                            tint = Forest
                        )
                    }
                    IconButton(onClick = onCategories) {
                        Icon(
                            Icons.Outlined.ViewAgenda,
                            contentDescription = Ru.categoriesCd,
                            tint = Forest
                        )
                    }
                    IconButton(onClick = onGuide) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = Ru.howToWorkCd,
                            tint = Forest
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createBlank(onOpen) },
                containerColor = Forest,
                contentColor = Sand,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = Ru.newResentmentCd)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AtmosphereBackground(modifier = Modifier.fillMaxSize())

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SummaryHeader(
                        total = state.total,
                        completed = state.completed
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(Ru.searchResentmentsHint) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = Forest)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Forest,
                            unfocusedBorderColor = Moss.copy(alpha = 0.35f),
                            focusedContainerColor = Sand.copy(alpha = 0.7f),
                            unfocusedContainerColor = Sand.copy(alpha = 0.45f),
                            cursorColor = Forest
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CategoryFilters(
                        categories = state.categories,
                        selected = state.filter,
                        onSelect = viewModel::setFilter
                    )
                }

                if (state.items.isEmpty()) {
                    item {
                        when {
                            state.searchQuery.isNotBlank() -> {
                                Text(
                                    text = Ru.emptySearch,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                            state.filter == CategoryFilter.All && state.total == 0 -> {
                                EmptyState(onAdd = { viewModel.createBlank(onOpen) })
                            }
                            else -> {
                                Text(
                                    text = Ru.emptyCategoryList,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(state.items, key = { _, item -> item.resentment.id }) { index, item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 4 }
                        ) {
                            ResentmentRow(
                                item = item,
                                index = index + 1,
                                categoryName = item.resentment.categoryId?.let { categoryNames[it] },
                                showCategory = state.filter == CategoryFilter.All,
                                onClick = { onOpen(item.resentment.id) },
                                onDelete = { pendingDelete = item.resentment }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }

            pendingDelete?.let { item ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text(Ru.deleteTitle) },
                    text = { Text(Ru.deleteBody) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.delete(item)
                            pendingDelete = null
                        }) {
                            Text(Ru.delete, color = Danger)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text(Ru.cancel)
                        }
                    }
                )
            }

            if (state.importing) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(Ru.importing) },
                    text = {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Forest)
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }
}

@Composable
private fun CategoryFilters(
    categories: List<Category>,
    selected: CategoryFilter,
    onSelect: (CategoryFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected is CategoryFilter.All,
            onClick = { onSelect(CategoryFilter.All) },
            label = { Text(Ru.allCategories) },
            colors = chipColors()
        )
        categories.forEach { category ->
            FilterChip(
                selected = selected == CategoryFilter.ById(category.id),
                onClick = { onSelect(CategoryFilter.ById(category.id)) },
                label = { Text(category.name) },
                colors = chipColors()
            )
        }
        FilterChip(
            selected = selected is CategoryFilter.Uncategorized,
            onClick = { onSelect(CategoryFilter.Uncategorized) },
            label = { Text(Ru.uncategorized) },
            colors = chipColors()
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Forest,
    selectedLabelColor = Sand,
    containerColor = Sand.copy(alpha = 0.75f),
    labelColor = Forest
)

@Composable
private fun SummaryHeader(total: Int, completed: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Forest)
            .padding(18.dp)
    ) {
        Text(
            text = Ru.inventoryTitle,
            style = MaterialTheme.typography.titleLarge,
            color = Sand
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = Ru.inventorySubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Sand.copy(alpha = 0.82f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatChip(label = Ru.total, value = total.toString())
            Spacer(modifier = Modifier.width(10.dp))
            StatChip(label = Ru.done, value = completed.toString())
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Sand.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Sand.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Amber)
    }
}

@Composable
private fun ResentmentRow(
    item: ResentmentListItem,
    index: Int,
    categoryName: String?,
    showCategory: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sand.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (item.resentment.isCompleted) Moss.copy(alpha = 0.25f) else Forest.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Forest
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (showCategory) {
                    Text(
                        text = categoryName ?: Ru.uncategorized,
                        style = MaterialTheme.typography.labelMedium,
                        color = Amber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = item.resentment.target.ifBlank { Ru.untitled },
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.preview.ifBlank { Ru.causeEmpty },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Danger)
            }
            Icon(
                imageVector = if (item.resentment.isCompleted) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (item.resentment.isCompleted) Moss else MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        ProgressBar(current = item.progress, total = item.totalSteps)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = Ru.emptyTitle,
            style = MaterialTheme.typography.displayLarge,
            color = Forest
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = Ru.emptyBody,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = Ru.emptyCta,
            style = MaterialTheme.typography.titleMedium,
            color = Amber,
            modifier = Modifier.clickable(onClick = onAdd)
        )
    }
}
