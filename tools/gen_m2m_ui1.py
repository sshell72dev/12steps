# -*- coding: utf-8 -*-
"""UI: EditViewModel, type autocomplete, type pick dialog, EditScreen, SituationEdit*"""
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def esc(s: str) -> str:
    return "".join(f"\\u{ord(c):04x}" if ord(c) > 127 else c for c in s)


def w(rel, content):
    content = content.replace("UI_MODIFIER", MOD)
    (ROOT / rel).write_text(content, encoding="utf-8", newline="\n")
    print("wrote", rel)


# Ru strings
ru_path = ROOT / "Ru.kt"
ru = ru_path.read_text(encoding="utf-8")
extras = {
    "typeSearchHint": "Начните вводить тип…",
    "typeCatalogHint": "При фокусе — все типы по алфавиту",
    "quickSituationHint": "Опишите ситуацию своими словами",
    "suggestTypes": "Подбрать типы",
    "pickTypesTitle": "Типы для ситуации",
    "pickTypesExisting": "Имеющиеся типы",
    "pickTypesAi": "Предложения ИИ",
    "noTypeMatches": "Нет совпадений",
    "addTypeAction": "Добавить тип",
    "situationTypesLabel": "Типы этой ситуации",
    "untaggedSituation": "Без типа",
}
for key, text in extras.items():
    if f"const val {key}" not in ru:
        ru = ru.replace(
            "    const val typesHint",
            f'    const val {key} = "{esc(text)}"\n    const val typesHint',
            1,
        )
        print("ru+", key)
ru_path.write_text(ru, encoding="utf-8", newline="\n")

w(
    "ui/TypeAutocompleteField.kt",
    r'''package ru.na.step4.obidy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.UI_MODIFIER
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.TypeSuggestEngine
import ru.na.step4.obidy.ui.theme.Forest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeAutocompleteField(
    catalog: List<String>,
    onPick: (String) -> Unit,
    modifier: UI_MODIFIER = UI_MODIFIER
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
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = focused
            },
            modifier = UI_MODIFIER
                .menuAnchor()
                .fillMaxWidth()
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) expanded = true
                },
            label = { Text(Ru.addTypeAction) },
            placeholder = { Text(Ru.typeSearchHint) },
            supportingText = { Text(Ru.typeCatalogHint) },
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { if (!focused) expanded = false }
        ) {
            Column(
                modifier = UI_MODIFIER
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
''',
)

w(
    "ui/TypePickDialog.kt",
    r'''package ru.na.step4.obidy.ui

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
import androidx.compose.ui.UI_MODIFIER
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
                modifier = UI_MODIFIER
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
                        modifier = UI_MODIFIER
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
        modifier = UI_MODIFIER.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = UI_MODIFIER.weight(1f))
    }
}
''',
)

print("autocomplete+dialog ok")
