# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def esc(s: str) -> str:
    return "".join(f"\\u{ord(c):04x}" if ord(c) > 127 else c for c in s)


def write(rel: str, content: str):
    path = ROOT / rel
    content = content.replace("UI_MODIFIER", MOD)
    path.write_text(content.replace("\n", "\r\n"), encoding="utf-8")
    print("wrote", rel)


# Append Ru strings
ru_path = ROOT / "Ru.kt"
ru = ru_path.read_text(encoding="utf-8")
if "addSituation" not in ru:
    extra = f'''
    const val addSituation = "{esc("Добавить ситуацию")}"
    const val situationTitle = "{esc("Краткое название")}"
    const val situationTitleHint = "{esc("Одной фразой, чем была эта ситуация")}"
    const val deleteSituationTitle = "{esc("Удалить ситуацию?")}"
    const val deleteSituationBody = "{esc("Ответы по этой ситуации будут удалены.")}"
    const val addType = "{esc("Добавить тип")}"
    const val customTypeTitle = "{esc("Свой тип ситуации")}"
    const val customTypeHint = "{esc("Например: критика на работе")}"
    const val deleteTypeTitle = "{esc("Удалить тип ситуации?")}"
    const val deleteTypeBody = "{esc("Все ситуации этого типа будут удалены.")}"
    const val noTypesYet = "{esc("Выберите или добавьте тип ситуации")}"
    const val noSituationsYet = "{esc("Пока нет ситуаций — добавьте первую")}"
    const val treeEmpty = "{esc("Типы и ситуации ещё не добавлены")}"
    const val confirm = "{esc("Готово")}"
'''
    ru = ru.replace(
        "    const val micPermissionNeeded",
        extra + "\n    const val micPermissionNeeded",
    )
    # also update causeEmpty / hintWork
    ru = ru.replace(
        f'const val causeEmpty = "{esc("Ситуация ещё не описана")}"',
        f'const val causeEmpty = "{esc("Типы и ситуации ещё не добавлены")}"',
    )
    # if old causeEmpty still there
    if "treeEmpty" not in ru:
        pass
    ru_path.write_text(ru.replace("\n", "\r\n") if "\r\n" not in ru[:50] else ru, encoding="utf-8")
    print("updated Ru.kt")
else:
    print("Ru.kt already has addSituation")


write(
    "ui/EditViewModel.kt",
    r'''package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.SituationType
import ru.na.step4.obidy.data.TypeWithSituations

data class EditUiState(
    val id: Long = 0,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val target: String = "",
    val tree: List<TypeWithSituations> = emptyList(),
    val notes: String = "",
    val isCompleted: Boolean = false,
    val loaded: Boolean = false,
    val saved: Boolean = false
) {
    val progress: Int
        get() {
            var n = 0
            if (target.isNotBlank()) n++
            n += tree.sumOf { branch -> branch.situations.sumOf { it.progressSteps } }
            return n
        }

    val totalSteps: Int
        get() {
            val situations = tree.sumOf { it.situations.size }
            return 1 + situations.coerceAtLeast(1) * Situation.TOTAL_STEPS
        }
}

class EditViewModel(
    private val repository: ResentmentRepository,
    private val resentmentId: Long
) : ViewModel() {

    private val form = MutableStateFlow(EditUiState(id = resentmentId))

    val uiState: StateFlow<EditUiState> = combine(
        form,
        repository.observeCategories(),
        repository.observeTree(resentmentId.coerceAtLeast(0L))
    ) { state, categories, tree ->
        val effectiveTree = if (state.id > 0) tree else emptyList()
        state.copy(categories = categories, tree = effectiveTree)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EditUiState(id = resentmentId)
    )

    init {
        if (resentmentId > 0) {
            viewModelScope.launch {
                repository.getById(resentmentId)?.let { item ->
                    form.update {
                        it.copy(
                            id = item.id,
                            categoryId = item.categoryId,
                            target = item.target,
                            notes = item.notes,
                            isCompleted = item.isCompleted,
                            loaded = true
                        )
                    }
                }
            }
        } else {
            form.update { it.copy(loaded = true) }
        }
    }

    fun updateTarget(value: String) = form.update { it.copy(target = value, saved = false) }
    fun updateNotes(value: String) = form.update { it.copy(notes = value, saved = false) }

    fun setCategory(categoryId: Long?) {
        form.update { it.copy(categoryId = categoryId, saved = false) }
    }

    fun toggleCompleted() {
        form.update { it.copy(isCompleted = !it.isCompleted, saved = false) }
    }

    fun addType(name: String) {
        viewModelScope.launch {
            val id = ensureResentmentId()
            if (id > 0) repository.addType(id, name)
        }
    }

    fun removeType(type: SituationType) {
        viewModelScope.launch {
            repository.deleteType(type)
        }
    }

    fun addSituation(typeId: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            ensureResentmentId()
            val id = repository.addSituation(typeId)
            onCreated(id)
        }
    }

    fun save(onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = ensureResentmentId()
            form.update { it.copy(id = id, saved = true, loaded = true) }
            onSaved(id)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val state = form.value
            if (state.id > 0) {
                repository.getById(state.id)?.let { repository.delete(it) }
            }
            onDeleted()
        }
    }

    private suspend fun ensureResentmentId(): Long {
        val state = form.value
        val entity = Resentment(
            id = state.id,
            categoryId = state.categoryId,
            target = state.target.trim(),
            notes = state.notes.trim(),
            isCompleted = state.isCompleted
        )
        val id = repository.save(entity)
        if (state.id != id) {
            form.update { it.copy(id = id) }
        }
        return id
    }

    companion object {
        fun factory(repository: ResentmentRepository, id: Long) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EditViewModel(repository, id) as T
                }
            }
    }
}
''',
)

write(
    "ui/EditScreen.kt",
    r'''package ru.na.step4.obidy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.UI_MODIFIER
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.SituationType
import ru.na.step4.obidy.data.TypeWithSituations
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.FieldBlock
import ru.na.step4.obidy.ui.components.ProgressBar
import ru.na.step4.obidy.ui.components.SectionHint
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
    var showCustomType by remember { mutableStateOf(false) }
    var customTypeName by remember { mutableStateOf("") }
    var typeToDelete by remember { mutableStateOf<SituationType?>(null) }

    Scaffold(
        containerColor = Sand,
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
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = Ru.back,
                            tint = Forest
                        )
                    }
                },
                actions = {
                    if (state.id > 0) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = Ru.delete,
                                tint = Danger
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(
            modifier = UI_MODIFIER
                .fillMaxSize()
                .padding(padding)
        ) {
            AtmosphereBackground(modifier = UI_MODIFIER.fillMaxSize())

            Column(
                modifier = UI_MODIFIER
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                SectionHint(Ru.hintWork)
                ProgressBar(current = state.progress, total = state.totalSteps)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Ru.categoryLabel, style = MaterialTheme.typography.labelLarge, color = Amber)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.categoryId == null,
                            onClick = { viewModel.setCategory(null) },
                            label = { Text(Ru.uncategorized) },
                            colors = chipColors()
                        )
                        state.categories.forEach { category ->
                            FilterChip(
                                selected = state.categoryId == category.id,
                                onClick = { viewModel.setCategory(category.id) },
                                label = { Text(category.name) },
                                colors = chipColors()
                            )
                        }
                    }
                }

                FieldBlock(
                    step = InventoryStructure.POINT_A,
                    title = InventoryStructure.TARGET_TITLE,
                    hint = InventoryStructure.TARGET_HINT,
                    value = state.target,
                    onValueChange = viewModel::updateTarget,
                    minLines = 2
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        InventoryStructure.TYPE_SECTION,
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest
                    )
                    Text(
                        InventoryStructure.TYPE_SECTION_HINT,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InventoryStructure.suggestedSituationTypes.forEach { name ->
                            val selected = state.tree.any { it.type.name == name }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        state.tree.firstOrNull { it.type.name == name }?.let {
                                            typeToDelete = it.type
                                        }
                                    } else {
                                        viewModel.addType(name)
                                    }
                                },
                                label = { Text(name) },
                                colors = chipColors()
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = { showCustomType = true },
                            label = { Text(InventoryStructure.TYPE_CUSTOM) },
                            colors = chipColors()
                        )
                    }
                    if (state.tree.isEmpty()) {
                        Text(
                            Ru.noTypesYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.tree.forEach { branch ->
                    TypeBranchBlock(
                        branch = branch,
                        onDeleteType = { typeToDelete = branch.type },
                        onAddSituation = {
                            viewModel.addSituation(branch.type.id, onOpenSituation)
                        },
                        onOpenSituation = onOpenSituation
                    )
                }

                FieldBlock(
                    step = Ru.notes,
                    title = Ru.notesTitle,
                    hint = Ru.notesHint,
                    value = state.notes,
                    onValueChange = viewModel::updateNotes,
                    minLines = 3
                )

                Row(
                    modifier = UI_MODIFIER.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = UI_MODIFIER.weight(1f)) {
                        Text(Ru.completed, style = MaterialTheme.typography.titleMedium, color = Forest)
                        Text(
                            Ru.completedHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isCompleted,
                        onCheckedChange = { viewModel.toggleCompleted() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Moss,
                            checkedThumbColor = Sand
                        )
                    )
                }

                Button(
                    onClick = { viewModel.save { onBack() } },
                    modifier = UI_MODIFIER
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Forest,
                        contentColor = Sand
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(Ru.save, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = UI_MODIFIER.height(24.dp))
            }
        }
    }

    if (showCustomType) {
        AlertDialog(
            onDismissRequest = { showCustomType = false },
            title = { Text(Ru.customTypeTitle) },
            text = {
                OutlinedTextField(
                    value = customTypeName,
                    onValueChange = { customTypeName = it },
                    placeholder = { Text(Ru.customTypeHint) },
                    singleLine = true,
                    modifier = UI_MODIFIER.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addType(customTypeName)
                    customTypeName = ""
                    showCustomType = false
                }) { Text(Ru.confirm, color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomType = false }) {
                    Text(Ru.cancel, color = Forest)
                }
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
                TextButton(onClick = { typeToDelete = null }) {
                    Text(Ru.cancel, color = Forest)
                }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(Ru.deleteTitle) },
            text = { Text(Ru.deleteBody) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete(onBack)
                }) {
                    Text(Ru.delete, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(Ru.cancel, color = Forest)
                }
            }
        )
    }
}

@Composable
private fun TypeBranchBlock(
    branch: TypeWithSituations,
    onDeleteType: () -> Unit,
    onAddSituation: () -> Unit,
    onOpenSituation: (Long) -> Unit
) {
    Column(
        modifier = UI_MODIFIER.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = UI_MODIFIER.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = UI_MODIFIER.weight(1f)) {
                Text(branch.type.name, style = MaterialTheme.typography.titleMedium, color = Forest)
                Text(
                    InventoryStructure.SITUATION_SECTION,
                    style = MaterialTheme.typography.labelMedium,
                    color = Amber
                )
            }
            IconButton(onClick = onAddSituation) {
                Icon(Icons.Outlined.Add, contentDescription = Ru.addSituation, tint = Forest)
            }
            IconButton(onClick = onDeleteType) {
                Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Danger)
            }
        }
        if (branch.situations.isEmpty()) {
            Text(
                Ru.noSituationsYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            branch.situations.forEach { situation ->
                SituationRow(situation = situation, onClick = { onOpenSituation(situation.id) })
            }
        }
        TextButton(onClick = onAddSituation) {
            Text(Ru.addSituation, color = Forest)
        }
    }
}

@Composable
private fun SituationRow(situation: Situation, onClick: () -> Unit) {
    Column(
        modifier = UI_MODIFIER
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            situation.preview,
            style = MaterialTheme.typography.bodyLarge,
            color = Forest,
            maxLines = 2
        )
        Spacer(modifier = UI_MODIFIER.height(6.dp))
        ProgressBar(current = situation.progressSteps, total = Situation.TOTAL_STEPS)
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Forest,
    selectedLabelColor = Sand,
    containerColor = Sand.copy(alpha = 0.7f),
    labelColor = Forest
)
''',
)

# Fix SituationEditScreen Modifier
sit = (ROOT / "ui/SituationEditScreen.kt").read_text(encoding="utf-8")
sit = sit.replace("import androidx.compose.ui.modifier", f"import androidx.compose.ui.{MOD}")
sit = sit.replace("Box(modifier.", f"Box({MOD}.")
sit = sit.replace("AtmosphereBackground(modifier.", f"AtmosphereBackground({MOD}.")
sit = sit.replace("Modifier = Modifier.", f"modifier = {MOD}.")
sit = sit.replace("Spacer(Modifier.", f"Spacer({MOD}.")
# named params leftover
sit = sit.replace("modifier.fillMaxSize()", f"{MOD}.fillMaxSize()")
(ROOT / "ui/SituationEditScreen.kt").write_text(sit.replace("\n", "\r\n") if "\r\n" not in sit[:80] else sit, encoding="utf-8")
print("fixed SituationEditScreen")

write(
    "ui/Nav.kt",
    r'''package ru.na.step4.obidy.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.na.step4.obidy.Step4App

private object Routes {
    const val LIST = "list"
    const val GUIDE = "guide"
    const val CATEGORIES = "categories"
    const val ASSISTANT = "assistant"
    const val EDIT = "edit/{id}"
    const val SITUATION = "situation/{id}"
    fun edit(id: Long) = "edit/$id"
    fun situation(id: Long) = "situation/$id"
}

@Composable
fun Step4Nav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as Step4App
    val repository = app.repository
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as ComponentActivity

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            val vm: ListViewModel = viewModel(factory = ListViewModel.factory(repository))
            ListScreen(
                viewModel = vm,
                onOpen = { id -> navController.navigate(Routes.edit(id)) },
                onGuide = { navController.navigate(Routes.GUIDE) },
                onCategories = { navController.navigate(Routes.CATEGORIES) },
                onAssistant = { navController.navigate(Routes.ASSISTANT) }
            )
        }
        composable(Routes.GUIDE) {
            GuideScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CATEGORIES) {
            val vm: CategoriesViewModel = viewModel(
                factory = CategoriesViewModel.factory(repository)
            )
            CategoriesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ASSISTANT) {
            val vm: AssistantViewModel = viewModel(
                factory = AssistantViewModel.factory(
                    app = app as Application,
                    repository = repository
                )
            )
            DisposableEffect(activity, lifecycleOwner) {
                vm.attachHost(activity, lifecycleOwner.lifecycle)
                onDispose { }
            }
            AssistantScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenResentment = { id ->
                    navController.navigate(Routes.edit(id)) {
                        popUpTo(Routes.LIST)
                    }
                }
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: EditViewModel = viewModel(
                factory = EditViewModel.factory(repository, id)
            )
            EditScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenSituation = { situationId ->
                    navController.navigate(Routes.situation(situationId))
                }
            )
        }
        composable(
            route = Routes.SITUATION,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: SituationEditViewModel = viewModel(
                factory = SituationEditViewModel.factory(repository, id)
            )
            SituationEditScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
''',
)

print("ui done")
