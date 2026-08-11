# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")

# AssistantViewModel
avm = ROOT / "ui/AssistantViewModel.kt"
a = avm.read_text(encoding="utf-8")
old = '''    fun createResentmentFromDraft(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val s = session.value
            val resentmentId = repository.save(Resentment(target = s.draftTarget, notes = ""))
            val typeId = repository.addType(resentmentId, "\u041e\u0431\u0449\u0435\u0435") ?: return@launch
            var situation = Situation(
                typeId = typeId,
                whatHappened = s.draftWhat,
                iFelt = s.draftFelt,
                iDid = s.draftDid
            )
            s.draftAnswers.forEach { (number, value) -> situation = situation.withAnswer(number, value) }
            repository.saveSituation(situation)
            onCreated(resentmentId)
        }
    }'''
new = '''    fun createResentmentFromDraft(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val s = session.value
            val resentmentId = repository.save(Resentment(target = s.draftTarget, notes = ""))
            val typeName = s.draftSituationType.ifBlank { "\u041e\u0431\u0449\u0430\u044f" }
            val typeId = repository.addType(resentmentId, typeName)
            var situation = Situation(
                resentmentId = resentmentId,
                whatHappened = s.draftWhat,
                iFelt = s.draftFelt,
                iDid = s.draftDid
            )
            s.draftAnswers.forEach { (number, value) -> situation = situation.withAnswer(number, value) }
            val situationId = repository.saveSituation(situation)
            if (typeId > 0) repository.linkSituationToType(situationId, typeId)
            onCreated(resentmentId)
        }
    }'''
if old not in a:
    raise SystemExit("assistant block missing:\n" + a[a.find("createResentmentFromDraft"):a.find("createResentmentFromDraft")+500])
avm.write_text(a.replace(old, new), encoding="utf-8", newline="\n")
print("assistant ok")

# TypeAutocomplete imports + expand on focus
tac = ROOT / "ui/TypeAutocompleteField.kt"
t = tac.read_text(encoding="utf-8")
if "ExposedDropdownMenu\n" not in t and "material3.ExposedDropdownMenu" not in t:
    t = t.replace(
        "import androidx.compose.material3.ExposedDropdownMenuBox\n",
        "import androidx.compose.material3.ExposedDropdownMenu\nimport androidx.compose.material3.ExposedDropdownMenuBox\n",
    )
t = t.replace(
    "expanded = focused && filtered.isNotEmpty()",
    "expanded = focused",
)
t = t.replace("import androidx.compose.foundation.clickable\n", "")
tac.write_text(t, encoding="utf-8", newline="\n")
print("autocomplete ok")

# SituationEditViewModel: observe all types by resentmentId
sev = ROOT / "ui/SituationEditViewModel.kt"
# rewrite cleaner
sev.write_text(
    r'''package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.SituationType

data class SituationEditUiState(
    val id: Long = 0,
    val resentmentId: Long = 0,
    val title: String = "",
    val whatHappened: String = "",
    val iFelt: String = "",
    val iDid: String = "",
    val answers: Map<Int, String> = emptyMap(),
    val linkedTypes: List<SituationType> = emptyList(),
    val allTypes: List<SituationType> = emptyList(),
    val loaded: Boolean = false
) {
    val progress: Int
        get() = toSituation().progressSteps

    val suggestText: String
        get() = listOf(title, whatHappened, iFelt).filter { it.isNotBlank() }.joinToString(" ")

    fun toSituation() = Situation(
        id = id,
        resentmentId = resentmentId,
        title = title,
        whatHappened = whatHappened,
        iFelt = iFelt,
        iDid = iDid
    ).let { base ->
        answers.entries.fold(base) { item, (number, value) -> item.withAnswer(number, value) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SituationEditViewModel(
    private val repository: ResentmentRepository,
    private val situationId: Long
) : ViewModel() {
    private val form = MutableStateFlow(SituationEditUiState(id = situationId))

    private val allTypesFlow = form.map { it.resentmentId }.flatMapLatest { rid ->
        if (rid > 0) repository.observeTypes(rid) else flowOf(emptyList())
    }

    val uiState: StateFlow<SituationEditUiState> = combine(
        form,
        repository.observeTypesForSituation(situationId),
        allTypesFlow
    ) { state, linked, allTypes ->
        state.copy(linkedTypes = linked, allTypes = allTypes)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SituationEditUiState(id = situationId)
    )

    init {
        viewModelScope.launch {
            repository.getSituation(situationId)?.let { item ->
                form.update {
                    it.copy(
                        id = item.id,
                        resentmentId = item.resentmentId,
                        title = item.title,
                        whatHappened = item.whatHappened,
                        iFelt = item.iFelt,
                        iDid = item.iDid,
                        answers = (1..13).associateWith(item::answerFor),
                        loaded = true
                    )
                }
            }
        }
    }

    fun updateTitle(value: String) = form.update { it.copy(title = value) }
    fun updateWhatHappened(value: String) = form.update { it.copy(whatHappened = value) }
    fun updateIFelt(value: String) = form.update { it.copy(iFelt = value) }
    fun updateIDid(value: String) = form.update { it.copy(iDid = value) }
    fun updateAnswer(number: Int, value: String) =
        form.update { it.copy(answers = it.answers + (number to value)) }

    fun applyTypes(selectedExistingIds: Set<Long>, selectedProposedNames: Set<String>) {
        viewModelScope.launch {
            val rid = form.value.resentmentId
            repository.applyTypeSelection(
                rid, situationId, selectedExistingIds, selectedProposedNames
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveSituation(form.value.toSituation().trimmed())
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.getSituation(situationId)?.let { repository.deleteSituation(it) }
            onDeleted()
        }
    }

    private fun Situation.trimmed() = copy(
        title = title.trim(),
        whatHappened = whatHappened.trim(),
        iFelt = iFelt.trim(),
        iDid = iDid.trim()
    ).let { value ->
        (1..13).fold(value) { item, number -> item.withAnswer(number, item.answerFor(number).trim()) }
    }

    companion object {
        fun factory(repository: ResentmentRepository, id: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SituationEditViewModel(repository, id) as T
        }
    }
}
''',
    encoding="utf-8",
    newline="\n",
)
print("SEVM rewritten")
