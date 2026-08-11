# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def esc(s: str) -> str:
    return "".join(f"\\u{ord(c):04x}" if ord(c) > 127 else c for c in s)


def w(rel, content):
    content = content.replace("UI_MODIFIER", MOD)
    (ROOT / rel).write_text(content, encoding="utf-8", newline="\n")
    print("wrote", rel)


ru_path = ROOT / "Ru.kt"
ru = ru_path.read_text(encoding="utf-8")
for key, text in [("notes", "Заметки"), ("markDone", "Разобрано")]:
    if f"const val {key}" not in ru:
        ru = ru.replace(
            "    const val typesHint",
            f'    const val {key} = "{esc(text)}"\n    const val typesHint',
            1,
        )
        print("ru+", key)
ru_path.write_text(ru, encoding="utf-8", newline="\n")

w(
    "ui/SituationEditScreen.kt",
    r'''package ru.na.step4.obidy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.UI_MODIFIER
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.FieldBlock
import ru.na.step4.obidy.ui.components.ProgressBar
import ru.na.step4.obidy.ui.components.SectionHint
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SituationEditScreen(
    viewModel: SituationEditViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var showTypePick by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Sand,
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
        }
    ) { padding ->
        Box(UI_MODIFIER.fillMaxSize().padding(padding)) {
            AtmosphereBackground(UI_MODIFIER.fillMaxSize())
            Column(
                UI_MODIFIER
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SectionHint(InventoryStructure.SITUATION_SECTION_HINT)
                ProgressBar(state.progress, Situation.TOTAL_STEPS)

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

                FieldBlock("", Ru.situationTitle, Ru.situationTitleHint, state.title, viewModel::updateTitle, 1)
                FieldBlock(
                    InventoryStructure.POINT_B,
                    InventoryStructure.WHAT_TITLE,
                    InventoryStructure.WHAT_HINT,
                    state.whatHappened,
                    viewModel::updateWhatHappened
                )
                FieldBlock(
                    InventoryStructure.POINT_B,
                    InventoryStructure.FELT_TITLE,
                    InventoryStructure.FELT_HINT,
                    state.iFelt,
                    viewModel::updateIFelt
                )
                FieldBlock(
                    InventoryStructure.POINT_B,
                    InventoryStructure.DID_TITLE,
                    InventoryStructure.DID_HINT,
                    state.iDid,
                    viewModel::updateIDid
                )
                Text(InventoryStructure.Q_SECTION, style = MaterialTheme.typography.titleMedium, color = Forest)
                Text(InventoryStructure.Q_SECTION_HINT, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InventoryStructure.questions.forEach { question ->
                    FieldBlock(
                        if (question.number <= 4) InventoryStructure.POINT_B else InventoryStructure.POINT_V,
                        question.title,
                        question.hint,
                        state.answers[question.number].orEmpty(),
                        { viewModel.updateAnswer(question.number, it) }
                    )
                }
                Button(
                    onClick = { viewModel.save(onBack) },
                    modifier = UI_MODIFIER.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(Ru.save) }
                Spacer(UI_MODIFIER.height(24.dp))
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
''',
)

# Fix AssistantViewModel
avm = ROOT / "ui/AssistantViewModel.kt"
a = avm.read_text(encoding="utf-8")
old = '''            val item = Resentment(target = s.draftTarget)
            val id = repository.save(item)
            val typeId = repository.addType(
                id,
                s.draftSituationType.ifBlank { "\u041e\u0431\u0449\u0430\u044f" }
            )
            var situation = Situation(
                typeId = typeId,
                whatHappened = s.draftWhat,
                iFelt = s.draftFelt,
                iDid = s.draftDid
            )
            s.draftAnswers.forEach { (num, value) ->
                situation = situation.withAnswer(num, value)
            }
            repository.saveSituation(situation)
            onCreated(id)'''
new = '''            val item = Resentment(target = s.draftTarget)
            val id = repository.save(item)
            val typeName = s.draftSituationType.ifBlank { "\u041e\u0431\u0449\u0430\u044f" }
            val typeId = repository.addType(id, typeName)
            var situation = Situation(
                resentmentId = id,
                whatHappened = s.draftWhat,
                iFelt = s.draftFelt,
                iDid = s.draftDid
            )
            s.draftAnswers.forEach { (num, value) ->
                situation = situation.withAnswer(num, value)
            }
            val situationId = repository.saveSituation(situation)
            if (typeId > 0) {
                repository.linkSituationToType(situationId, typeId)
            }
            onCreated(id)'''
if old in a:
    avm.write_text(a.replace(old, new), encoding="utf-8", newline="\n")
    print("AssistantViewModel fixed")
else:
    print("AssistantViewModel block not found")
    # try softer
    if "typeId = typeId" in a:
        print("still has typeId assignment")
