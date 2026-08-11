package ru.na.step4.obidy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.ResentmentSearch
import ru.na.step4.obidy.ui.components.HintIcon
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    knownTargets: List<String>,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    catalogHint: String = "",
    minLines: Int = 1
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    val filtered = remember(value, knownTargets) {
        ResentmentSearch.filterTargets(value, knownTargets)
            .filter { !it.equals(value.trim(), ignoreCase = true) }
    }

    LaunchedEffect(focused, filtered) {
        expanded = focused && filtered.isNotEmpty()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            val tip = listOf(hint, catalogHint).filter { it.isNotBlank() }.joinToString("\n\n")
            if (tip.isNotEmpty()) {
                HintIcon(tip)
            }
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it && focused },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = focused
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        if (state.isFocused) expanded = true
                    },
                minLines = minLines,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Forest,
                    unfocusedBorderColor = Moss.copy(alpha = 0.35f),
                    focusedContainerColor = Sand.copy(alpha = 0.7f),
                    unfocusedContainerColor = Sand.copy(alpha = 0.45f),
                    cursorColor = Forest
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { if (!focused) expanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (filtered.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(Ru.noTargetMatches) },
                            onClick = {}
                        )
                    } else {
                        filtered.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, color = Forest) },
                                onClick = {
                                    onValueChange(name)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
