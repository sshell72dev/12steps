package ru.na.step4.obidy.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.analysis.AnalysisBranch
import ru.na.step4.obidy.data.analysis.AnalysisCatalog
import ru.na.step4.obidy.data.analysis.AnalysisCatalogSync
import ru.na.step4.obidy.data.analysis.AnalysisFlow
import ru.na.step4.obidy.data.analysis.AnalysisSettings
import ru.na.step4.obidy.data.analysis.Choice
import ru.na.step4.obidy.data.analysis.CleanDayItem
import ru.na.step4.obidy.data.analysis.CleanDaySide
import ru.na.step4.obidy.data.analysis.LinearQuestion
import ru.na.step4.obidy.data.analysis.QuestionButtons
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@Composable
fun AnalysisCatalogTab(
    settings: AnalysisSettings,
    prefs: JournalPrefs,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by settings.revision.collectAsStateWithLifecycle()
    val items = remember(revision) { AnalysisCatalog.hubItems(context, settings) }
    val overrides = remember(revision) { settings.overrides() }
    val customIds = remember(revision) {
        AnalysisCatalog.resolved(context, settings).filter { it.custom }.map { it.id }.toSet()
    }
    var createOpen by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var cleanLong by remember(revision) { mutableStateOf(settings.cleanDayLong) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (prefs.isAdmin) Ru.analysisAdminHint else Ru.analysisEditHint,
            style = MaterialTheme.typography.bodyMedium,
            color = Moss
        )
        Text(
            Ru.analysisCleanDayVariant,
            style = MaterialTheme.typography.titleMedium,
            color = Forest
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !cleanLong,
                onClick = {
                    cleanLong = false
                    settings.cleanDayLong = false
                },
                label = { Text(Ru.analysisCleanDayShort) },
                colors = editorChipColors()
            )
            FilterChip(
                selected = cleanLong,
                onClick = {
                    cleanLong = true
                    settings.cleanDayLong = true
                },
                label = { Text(Ru.analysisCleanDayLong) },
                colors = editorChipColors()
            )
        }
        Button(
            onClick = {
                createTitle = ""
                createOpen = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(Ru.analysisCreate)
        }
        items.forEach { (id, title) ->
            val editId = if (id == AnalysisCatalog.MENU_CLEAN_DAY) settings.cleanDayId() else id
            val custom = editId in customIds
            val edited = overrides.containsKey(editId)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .clickable { onEdit(editId) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Forest)
                    val badge = when {
                        custom -> Ru.analysisCustomBadge
                        edited -> Ru.analysisEdited
                        else -> null
                    }
                    if (badge != null) {
                        Text(badge, style = MaterialTheme.typography.labelMedium, color = Amber)
                    }
                }
                if (custom) {
                    IconButton(onClick = { deleteId = editId }) {
                        Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
                    }
                }
            }
        }
    }
    if (createOpen) {
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text(Ru.analysisCreate) },
            text = {
                OutlinedTextField(
                    value = createTitle,
                    onValueChange = { createTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(Ru.analysisCreateTitle) },
                    placeholder = { Text(Ru.analysisCreateHint) },
                    singleLine = true,
                    colors = editorFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val created = if (prefs.isAdmin) {
                            val entry = settings.addStandardCustom(
                                createTitle,
                                AnalysisCatalog.loadDefaults(context)
                            )
                            scope.launch { AnalysisCatalogSync.push(settings, prefs) }
                            entry
                        } else {
                            settings.addCustom(createTitle)
                        }
                        createOpen = false
                        onEdit(created.id)
                    }
                ) { Text(Ru.save) }
            },
            dismissButton = {
                TextButton(onClick = { createOpen = false }) { Text(Ru.cancel) }
            }
        )
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(Ru.analysisDeleteCustomTitle) },
            text = { Text(Ru.analysisDeleteCustomBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (prefs.isAdmin) {
                            settings.removeStandardEntry(id, AnalysisCatalog.loadDefaults(context))
                            scope.launch { AnalysisCatalogSync.push(settings, prefs) }
                        } else {
                            settings.deleteCustom(id)
                        }
                        deleteId = null
                    }
                ) { Text(Ru.delete) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(Ru.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisEditScreen(
    viewModel: AnalysisEditorViewModel,
    onBack: () -> Unit
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val publish by viewModel.publish.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        entry.title.ifBlank { Ru.analysisCreate },
                        style = MaterialTheme.typography.titleLarge,
                        color = Forest
                    )
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = entry.title,
                    onValueChange = { text -> viewModel.update { it.copy(title = text) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(Ru.analysisTitleField) },
                    singleLine = true,
                    colors = editorFieldColors()
                )
                if (viewModel.isAdmin) {
                    Text(
                        Ru.analysisAdminHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Amber
                    )
                    Button(
                        onClick = viewModel::publishShared,
                        enabled = publish !is AnalysisPublishUi.Busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            when (publish) {
                                is AnalysisPublishUi.Busy -> Ru.analysisPublishBusy
                                else -> Ru.analysisPublishShared
                            }
                        )
                    }
                    when (publish) {
                        is AnalysisPublishUi.Ok -> Text(
                            Ru.analysisPublishOk,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Amber
                        )
                        is AnalysisPublishUi.Error -> Text(
                            Ru.analysisPublishFail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Unit
                    }
                }
                if (entry.custom) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(Ru.delete, color = Forest) }
                } else if (viewModel.overridden) {
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(Ru.analysisReset, color = Forest) }
                }
                when (entry.flow) {
                    AnalysisFlow.CLEAN_DAY -> CleanDayEditor(
                        items = entry.items,
                        onChange = { items -> viewModel.update { it.copy(items = items) } }
                    )
                    AnalysisFlow.BRANCHED -> {
                        Text(Ru.analysisCommonQuestions, color = Forest, style = MaterialTheme.typography.titleMedium)
                        QuestionEditorList(
                            questions = entry.questions,
                            onChange = { qs -> viewModel.update { it.copy(questions = qs) } }
                        )
                        Text(Ru.analysisBranches, color = Forest, style = MaterialTheme.typography.titleMedium)
                        BranchEditorList(
                            branches = entry.branches,
                            onChange = { list -> viewModel.update { it.copy(branches = list) } }
                        )
                    }
                    else -> {
                        Text(Ru.analysisQuestions, color = Forest, style = MaterialTheme.typography.titleMedium)
                        QuestionEditorList(
                            questions = entry.questions,
                            onChange = { qs -> viewModel.update { it.copy(questions = qs) } }
                        )
                    }
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(Ru.analysisResetTitle) },
            text = { Text(Ru.analysisResetBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reset()
                        confirmReset = false
                    }
                ) { Text(Ru.analysisReset) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(Ru.cancel) }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(Ru.analysisDeleteCustomTitle) },
            text = { Text(Ru.analysisDeleteCustomBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustom()
                        confirmDelete = false
                        onBack()
                    }
                ) { Text(Ru.delete) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(Ru.cancel) }
            }
        )
    }
}

@Composable
private fun QuestionEditorList(
    questions: List<LinearQuestion>,
    onChange: (List<LinearQuestion>) -> Unit,
    depth: Int = 0
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 52.dp.toPx() }

    fun targetIndex(from: Int, offset: Float): Int {
        if (from !in questions.indices) return from
        val steps = (offset / rowHeightPx).roundToInt()
        return (from + steps).coerceIn(0, questions.lastIndex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        questions.forEachIndexed { index, question ->
            key(question.id) {
            val isDragging = draggingIndex == index
            val dropAt = if (draggingIndex >= 0) targetIndex(draggingIndex, dragOffset) else -1
            val isDropTarget = dropAt == index && draggingIndex != index
            val shift = when {
                draggingIndex < 0 || isDragging -> 0f
                draggingIndex < index && dropAt >= index -> -rowHeightPx * 0.35f
                draggingIndex > index && dropAt <= index -> rowHeightPx * 0.35f
                else -> 0f
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 2f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffset
                            shadowElevation = 12f
                            scaleX = 1.02f
                            scaleY = 1.02f
                        } else {
                            translationY = shift
                        }
                    }
            ) {
                QuestionReorderRow(
                    questionId = question.id,
                    order = index + 1,
                    total = questions.size,
                    title = question.text,
                    buttonsBadge = buttonsBadge(question),
                    yesNoActive = isYesNo(question),
                    showYesNoHint = question.buttons == QuestionButtons.AUTO,
                    skipOnNo = question.skipNextOnNo > 0,
                    expanded = expandedId == question.id,
                    isDragging = isDragging,
                    isDropTarget = isDropTarget,
                    swapHint = if (isDragging && dropAt != index) {
                        Ru.analysisDraggingSwap.format(index + 1, dropAt + 1)
                    } else {
                        null
                    },
                    onOrderCommit = { order1Based ->
                        onChange(
                            questions.moved(
                                index,
                                (order1Based - 1).coerceIn(0, questions.lastIndex)
                            )
                        )
                    },
                    onToggleExpand = {
                        expandedId = if (expandedId == question.id) null else question.id
                    },
                    onDelete = { onChange(questions.filterIndexed { i, _ -> i != index }) },
                    onDragStart = {
                        expandedId = null
                        draggingIndex = index
                        dragOffset = 0f
                    },
                    onDrag = { amount -> dragOffset += amount },
                    onDragEnd = {
                        val from = draggingIndex
                        val to = targetIndex(from, dragOffset)
                        draggingIndex = -1
                        dragOffset = 0f
                        if (from in questions.indices && to != from) {
                            onChange(questions.moved(from, to))
                        }
                    }
                )
                if (expandedId == question.id) {
                    QuestionEditorDetails(
                        question = question,
                        depth = depth,
                        onChange = { next ->
                            onChange(questions.toMutableList().also { it[index] = next })
                        }
                    )
                }
            }
            }
        }
        OutlinedButton(
            onClick = {
                val blank = AnalysisCatalog.blankQuestion()
                onChange(questions + blank)
                expandedId = blank.id
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = Forest)
            Spacer(Modifier.width(8.dp))
            Text(Ru.analysisAddQuestion, color = Forest)
        }
    }
}

@Composable
private fun QuestionReorderRow(
    questionId: String,
    order: Int,
    total: Int,
    title: String,
    buttonsBadge: String,
    yesNoActive: Boolean,
    showYesNoHint: Boolean,
    skipOnNo: Boolean,
    expanded: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    swapHint: String?,
    onOrderCommit: (Int) -> Unit,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val focus = LocalFocusManager.current
    var orderDraft by remember(questionId, order) { mutableStateOf(order.toString()) }
    val borderColor = when {
        isDragging -> Amber
        isDropTarget -> Forest
        else -> Moss.copy(alpha = 0.25f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isDragging -> Sand
                    isDropTarget -> Forest.copy(alpha = 0.14f)
                    else -> SandDeep.copy(alpha = 0.72f)
                }
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = orderDraft,
                onValueChange = { raw ->
                    orderDraft = raw.filter { it.isDigit() }.take(3)
                },
                modifier = Modifier
                    .width(56.dp)
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            val n = orderDraft.toIntOrNull()?.coerceIn(1, total) ?: order
                            orderDraft = n.toString()
                            if (n != order) onOrderCommit(n)
                        }
                    },
                singleLine = true,
                label = { Text(Ru.analysisOrder) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val n = orderDraft.toIntOrNull()?.coerceIn(1, total) ?: order
                        orderDraft = n.toString()
                        if (n != order) onOrderCommit(n)
                        focus.clearFocus()
                    }
                ),
                colors = editorFieldColors()
            )
            Spacer(Modifier.width(6.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    title.ifBlank { Ru.analysisAddQuestion },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Forest,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        buttonsBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isYesNoBadge(buttonsBadge) || yesNoActive) Amber else Moss
                    )
                    if (showYesNoHint) {
                        Text(
                            "· ${Ru.analysisButtonsBadgeYesNo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber
                        )
                    }
                    if (skipOnNo) {
                        Text(
                            "· ↓",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber
                        )
                    }
                }
            }
            IconButton(onClick = onToggleExpand) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) {
                        Ru.analysisCollapseQuestion
                    } else {
                        Ru.analysisExpandQuestion
                    },
                    tint = Forest
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
            }
            DragHandleIcon(
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd
            )
        }
        if (swapHint != null) {
            Text(
                swapHint,
                style = MaterialTheme.typography.labelMedium,
                color = Amber,
                modifier = Modifier.padding(start = 62.dp, bottom = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionEditorDetails(
    question: LinearQuestion,
    depth: Int,
    onChange: (LinearQuestion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Sand.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = question.text,
            onValueChange = { onChange(question.copy(text = it)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            colors = editorFieldColors()
        )
        if (depth < 2) {
            Text(Ru.analysisButtons, style = MaterialTheme.typography.labelMedium, color = Amber)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonModeChip(Ru.analysisButtonsNone, question.buttons == QuestionButtons.NONE) {
                    onChange(question.copy(buttons = QuestionButtons.NONE, choices = emptyList()))
                }
                ButtonModeChip(Ru.analysisButtonsYesNo, isYesNo(question)) {
                    onChange(
                        question.copy(
                            buttons = QuestionButtons.LIST,
                            choices = AnalysisCatalog.yesNoChoices()
                        )
                    )
                }
                ButtonModeChip(
                    Ru.analysisButtonsCustom,
                    question.buttons == QuestionButtons.LIST && !isYesNo(question)
                ) {
                    val start = question.choices.ifEmpty { AnalysisCatalog.yesNoChoices() }
                    onChange(question.copy(buttons = QuestionButtons.LIST, choices = start))
                }
                if (depth == 0) {
                    ButtonModeChip(Ru.analysisButtonsAuto, question.buttons == QuestionButtons.AUTO) {
                        onChange(question.copy(buttons = QuestionButtons.AUTO))
                    }
                }
            }
            FilterChip(
                selected = question.allowText,
                onClick = { onChange(question.copy(allowText = !question.allowText)) },
                label = { Text(Ru.analysisAllowText) },
                colors = editorChipColors()
            )
            if (isYesNo(question) || question.buttons == QuestionButtons.AUTO) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        Ru.analysisSkipOnNo,
                        color = Forest,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = question.skipNextOnNo > 0,
                        onCheckedChange = { on ->
                            onChange(question.copy(skipNextOnNo = if (on) 1 else 0))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Sand,
                            checkedTrackColor = Forest,
                            uncheckedThumbColor = SandDeep,
                            uncheckedTrackColor = Moss.copy(alpha = 0.35f)
                        )
                    )
                }
            }
            if (question.buttons == QuestionButtons.LIST) {
                question.choices.forEach { choice ->
                    ChoiceEditor(
                        question = question,
                        choice = choice,
                        depth = depth,
                        onChange = onChange
                    )
                }
                OutlinedButton(
                    onClick = {
                        val next = Choice(AnalysisCatalog.newId(), Ru.analysisAddButton)
                        onChange(question.copy(choices = question.choices + next))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(Ru.analysisAddButton, color = Forest) }
            }
        }
    }
}

@Composable
private fun ChoiceEditor(
    question: LinearQuestion,
    choice: Choice,
    depth: Int,
    onChange: (LinearQuestion) -> Unit
) {
    val kids = question.followUps[choice.id].orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Sand.copy(alpha = 0.55f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = choice.label,
                onValueChange = { label ->
                    onChange(
                        question.copy(
                            choices = question.choices.map {
                                if (it.id == choice.id) it.copy(label = label) else it
                            }
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = editorFieldColors()
            )
            IconButton(
                onClick = {
                    onChange(
                        question.copy(
                            choices = question.choices.filterNot { it.id == choice.id },
                            followUps = question.followUps - choice.id,
                            skipNextByChoiceId = question.skipNextByChoiceId - choice.id,
                            endOnChoiceIds = question.endOnChoiceIds - choice.id
                        )
                    )
                }
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
            }
        }
        FilterChip(
            selected = choice.id in question.endOnChoiceIds,
            onClick = {
                val next = question.endOnChoiceIds.toMutableSet()
                if (choice.id in next) next.remove(choice.id) else next.add(choice.id)
                onChange(question.copy(endOnChoiceIds = next))
            },
            label = { Text(Ru.analysisEndOn) },
            colors = editorChipColors()
        )
        if (depth < 1) {
            Text(Ru.analysisFollowUps, style = MaterialTheme.typography.labelMedium, color = Amber)
            QuestionEditorList(
                questions = kids,
                onChange = { list ->
                    val follow = question.followUps.toMutableMap()
                    if (list.isEmpty()) follow.remove(choice.id) else follow[choice.id] = list
                    onChange(question.copy(followUps = follow))
                },
                depth = depth + 1
            )
        }
    }
}

@Composable
private fun BranchEditorList(
    branches: List<AnalysisBranch>,
    onChange: (List<AnalysisBranch>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        branches.forEachIndexed { index, branch ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DragHandle(index, branches.lastIndex) { from, to ->
                        onChange(branches.moved(from, to))
                    }
                    OutlinedTextField(
                        value = branch.title,
                        onValueChange = { title ->
                            onChange(
                                branches.toMutableList().also {
                                    it[index] = branch.copy(title = title)
                                }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(Ru.analysisBranchTitle) },
                        singleLine = true,
                        colors = editorFieldColors()
                    )
                    IconButton(
                        onClick = { onChange(branches.filterIndexed { i, _ -> i != index }) }
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
                    }
                }
                StringListEditor(
                    items = branch.questions,
                    onChange = { qs ->
                        onChange(
                            branches.toMutableList().also {
                                it[index] = branch.copy(questions = qs)
                            }
                        )
                    }
                )
            }
        }
        OutlinedButton(
            onClick = {
                onChange(
                    branches + AnalysisBranch(
                        id = AnalysisCatalog.newId(),
                        title = Ru.analysisAddBranch,
                        questions = listOf("Новый вопрос")
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text(Ru.analysisAddBranch, color = Forest) }
    }
}

@Composable
private fun CleanDayEditor(
    items: List<CleanDayItem>,
    onChange: (List<CleanDayItem>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(Ru.analysisCleanItems, color = Forest, style = MaterialTheme.typography.titleMedium)
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DragHandle(index, items.lastIndex) { from, to ->
                        onChange(items.moved(from, to))
                    }
                    OutlinedTextField(
                        value = item.title,
                        onValueChange = { title ->
                            onChange(items.toMutableList().also { it[index] = item.copy(title = title) })
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(Ru.analysisItemTitle) },
                        singleLine = true,
                        colors = editorFieldColors()
                    )
                    IconButton(
                        onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
                    }
                }
                OutlinedTextField(
                    value = item.question,
                    onValueChange = { q ->
                        onChange(items.toMutableList().also { it[index] = item.copy(question = q) })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = editorFieldColors()
                )
                SideEditor(Ru.analysisIfYes, item.ifYes) { side ->
                    onChange(items.toMutableList().also { it[index] = item.copy(ifYes = side) })
                }
                SideEditor(Ru.analysisIfNo, item.ifNo) { side ->
                    onChange(items.toMutableList().also { it[index] = item.copy(ifNo = side) })
                }
            }
        }
        OutlinedButton(
            onClick = {
                onChange(
                    items + CleanDayItem(
                        title = Ru.analysisAddItem,
                        question = "Новый вопрос",
                        ifYes = CleanDaySide(Ru.analysisYes, emptyList()),
                        ifNo = CleanDaySide(Ru.analysisNo, emptyList())
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text(Ru.analysisAddItem, color = Forest) }
    }
}

@Composable
private fun SideEditor(
    heading: String,
    side: CleanDaySide,
    onChange: (CleanDaySide) -> Unit
) {
    Text(heading, color = Amber, style = MaterialTheme.typography.labelMedium)
    OutlinedTextField(
        value = side.label,
        onValueChange = { onChange(side.copy(label = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(Ru.analysisSideLabel) },
        singleLine = true,
        colors = editorFieldColors()
    )
    StringListEditor(side.questions) { onChange(side.copy(questions = it)) }
}

@Composable
private fun StringListEditor(
    items: List<String>,
    onChange: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                DragHandle(index, items.lastIndex) { from, to ->
                    onChange(items.moved(from, to))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { next ->
                        onChange(items.toMutableList().also { it[index] = next })
                    },
                    modifier = Modifier.weight(1f),
                    minLines = 2,
                    colors = editorFieldColors()
                )
                IconButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(items + "Новый вопрос") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text(Ru.analysisAddQuestion, color = Forest) }
    }
}

@Composable
private fun ButtonModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = editorChipColors()
    )
}

@Composable
private fun DragHandleIcon(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Icon(
        Icons.Outlined.DragHandle,
        contentDescription = Ru.analysisMove,
        tint = Forest,
        modifier = Modifier
            .size(40.dp)
            .padding(4.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    }
                )
            }
    )
}

@Composable
private fun DragHandle(
    index: Int,
    lastIndex: Int,
    onMove: (Int, Int) -> Unit
) {
    var acc by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    Icon(
        Icons.Outlined.DragHandle,
        contentDescription = Ru.analysisMove,
        tint = Forest,
        modifier = Modifier
            .padding(end = 4.dp)
            .pointerInput(index, lastIndex) {
                detectVerticalDragGestures(
                    onDragEnd = { acc = 0f },
                    onDragCancel = { acc = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        acc += amount
                        when {
                            acc > threshold && index < lastIndex -> {
                                onMove(index, index + 1)
                                acc = 0f
                            }
                            acc < -threshold && index > 0 -> {
                                onMove(index, index - 1)
                                acc = 0f
                            }
                        }
                    }
                )
            }
    )
}

@Composable
private fun editorChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Forest,
    selectedLabelColor = Sand,
    containerColor = SandDeep,
    labelColor = Forest
)

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Forest,
    unfocusedBorderColor = Moss.copy(alpha = 0.35f),
    focusedContainerColor = Sand.copy(alpha = 0.7f),
    unfocusedContainerColor = Sand.copy(alpha = 0.45f),
    cursorColor = Forest
)

private fun buttonsBadge(question: LinearQuestion): String = when {
    isYesNo(question) -> Ru.analysisButtonsBadgeYesNo
    question.buttons == QuestionButtons.AUTO -> Ru.analysisButtonsBadgeAuto
    question.buttons == QuestionButtons.LIST -> Ru.analysisButtonsBadgeCustom
    else -> Ru.analysisButtonsBadgeNone
}

private fun isYesNoBadge(badge: String): Boolean =
    badge == Ru.analysisButtonsBadgeYesNo

private fun isYesNo(question: LinearQuestion): Boolean {
    if (question.buttons != QuestionButtons.LIST || question.choices.size != 2) return false
    val labels = question.choices.map { it.label.trim().lowercase() }.toSet()
    return labels == setOf("да", "нет")
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val copy = toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}