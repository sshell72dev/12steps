package ru.na.step4.obidy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.SituationType
import ru.na.step4.obidy.data.TypeSuggestEngine
import ru.na.step4.obidy.ui.theme.Forest

@Composable
fun TypePickDialog(
    situationText: String,
    existingTypes: List<SituationType>,
    initiallySelectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (selectedExistingIds: Set<Long>, selectedProposedNames: Set<String>) -> Unit
) {
    val result = remember(situationText, existingTypes) {
        TypeSuggestEngine.suggest(situationText, existingTypes)
    }
    val existingChecked = remember(initiallySelectedIds, result.existing) {
        mutableStateMapOf<Long, Boolean>().apply {
            result.existing.forEach { type ->
                this[type.id] = type.id in initiallySelectedIds
            }
        }
    }
    val proposedChecked = remember(result.proposed) {
        mutableStateMapOf<String, Boolean>().apply {
            result.proposed.forEach { name -> this[name] = false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Ru.pickTypesTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    Ru.pickTypesExisting,
                    style = MaterialTheme.typography.titleSmall,
                    color = Forest
                )
                if (result.existing.isEmpty()) {
                    Text(
                        Ru.noTypesYet,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    result.existing.forEach { type ->
                        CheckRow(
                            label = type.name,
                            checked = existingChecked[type.id] == true,
                            onCheckedChange = { existingChecked[type.id] = it }
                        )
                    }
                }
                if (result.proposed.isNotEmpty()) {
                    Text(
                        Ru.pickTypesAi,
                        style = MaterialTheme.typography.titleSmall,
                        color = Forest,
                        modifier = Modifier
                    )
                    result.proposed.forEach { name ->
                        CheckRow(
                            label = name,
                            checked = proposedChecked[name] == true,
                            onCheckedChange = { proposedChecked[name] = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        existingChecked.filter { it.value }.keys,
                        proposedChecked.filter { it.value }.keys
                    )
                }
            ) { Text(Ru.confirm, color = Forest) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Ru.cancel) }
        }
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}
