package ru.na.step4.obidy.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.confirmDelete.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Ru.manageCategories,
                        style = MaterialTheme.typography.titleLarge,
                        color = Forest
                    )
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.imeScaffoldContent(padding)
        ) {
            AtmosphereBackground(modifier = Modifier.fillMaxSize())

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Sand.copy(alpha = 0.9f))
                            .padding(16.dp)
                    ) {
                        Text(
                            Ru.addCategory,
                            style = MaterialTheme.typography.titleMedium,
                            color = Forest
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        VoiceOutlinedTextField(
                            value = state.draftName,
                            onValueChange = viewModel::updateDraftName,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(Ru.categoryNameHint) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Forest,
                                cursorColor = Forest
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::addCategory,
                            enabled = state.draftName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Forest,
                                contentColor = Sand
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(Ru.save)
                        }
                    }
                }

                if (state.items.isEmpty()) {
                    item {
                        Text(
                            Ru.emptyCategories,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    CategoryRow(
                        item = item,
                        editing = state.editingId == item.id,
                        editingName = state.editingName,
                        onEdit = { viewModel.startEdit(item) },
                        onDelete = { viewModel.requestDelete(item) },
                        onNameChange = viewModel::updateEditingName,
                        onSave = viewModel::saveEdit,
                        onCancel = viewModel::cancelEdit
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(Ru.deleteCategoryTitle) },
            text = { Text(Ru.deleteCategoryBody) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(Ru.delete, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(Ru.cancel, color = Forest)
                }
            }
        )
    }
}

@Composable
private fun CategoryRow(
    item: Category,
    editing: Boolean,
    editingName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sand.copy(alpha = 0.9f))
            .padding(14.dp)
    ) {
        if (editing) {
            VoiceOutlinedTextField(
                value = editingName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Forest,
                    cursorColor = Forest
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row {
                TextButton(onClick = onSave) {
                    Text(Ru.save, color = Forest)
                }
                TextButton(onClick = onCancel) {
                    Text(Ru.cancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = Ru.renameCategory, tint = Forest)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Danger)
                }
            }
        }
    }
}
