package ru.na.step4.obidy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.TypeSuggestEngine
import ru.na.step4.obidy.ui.components.HintIcon
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeAutocompleteField(
    catalog: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalogHint: String = ""
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    val filtered = remember(query, catalog) {
        TypeSuggestEngine.filterCatalog(query, catalog)
    }

    LaunchedEffect(focused, filtered) {
        expanded = focused && filtered.isNotEmpty()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && focused },
        modifier = modifier.fillMaxWidth()
    ) {
        VoiceOutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = focused
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) expanded = true
                },
            label = { Text(Ru.addTypeAction) },
            placeholder = { Text(Ru.typeSearchHint) },
            trailingIcon = if (catalogHint.isNotBlank()) {
                { ru.na.step4.obidy.ui.components.HintIcon(catalogHint) }
            } else {
                null
            },
            singleLine = true
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
                        text = { Text(Ru.noTypeMatches) },
                        onClick = {}
                    )
                } else {
                    filtered.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name, color = Forest) },
                            onClick = {
                                onPick(name)
                                query = ""
                                expanded = false
                            }
                        )
                    }
                }
                val trimmed = query.trim()
                if (trimmed.isNotEmpty() &&
                    filtered.none { it.equals(trimmed, ignoreCase = true) }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${Ru.addTypeAction}: $trimmed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Forest
                            )
                        },
                        onClick = {
                            onPick(trimmed)
                            query = ""
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
