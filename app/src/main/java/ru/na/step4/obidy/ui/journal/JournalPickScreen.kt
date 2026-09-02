package ru.na.step4.obidy.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.journal.NodeType
import ru.na.step4.obidy.data.journal.TreeNode
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalPickScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onSelected: () -> Unit,
    onResentments: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expandedStep by viewModel.expandedStep.collectAsStateWithLifecycle()
    val expandedChapter by viewModel.expandedChapter.collectAsStateWithLifecycle()
    val pickAllChapters by viewModel.pickAllChapters.collectAsStateWithLifecycle()
    val chapterShowsAllPoints by viewModel.chapterShowsAllPoints.collectAsStateWithLifecycle()
    val steps = viewModel.catalog.steps
    val path = state.path
    val currentId = path?.current?.id

    LaunchedEffect(Unit) {
        viewModel.syncPickExpansion()
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(JournalRu.pickEyebrow, style = MaterialTheme.typography.labelMedium, color = Amber)
                        Text(JournalRu.pickTitle, style = MaterialTheme.typography.headlineMedium, color = Forest)
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    NoteView(NoteIds.JOURNAL_PICK_HINT, JournalRu.pickHint, JournalRu.pickTitle)
                    if (path != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(path.line(), style = MaterialTheme.typography.bodyMedium, color = Forest)
                    }
                    if (path?.point != null) {
                        Spacer(Modifier.height(10.dp))
                        JournalButton(JournalRu.returnToCurrentPoint, onBack, filled = true)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                items(steps, key = { it.id }) { step ->
                    StepAccordion(
                        step = step,
                        expanded = expandedStep == step.id,
                        currentId = currentId,
                        count = viewModel.countFor(step.id),
                        chapterCount = { viewModel.countFor(it) },
                        onToggleStep = { viewModel.togglePickStep(step.id) },
                        onToggleChapter = viewModel::togglePickChapter,
                        isChapterExpanded = { chapterId ->
                            expandedStep == step.id && (pickAllChapters || expandedChapter == chapterId)
                        },
                        visiblePoints = { chapter ->
                            chapterShowsAllPoints
                            viewModel.visiblePickPoints(chapter)
                        },
                        onSelect = { viewModel.selectPickNode(it, onSelected) },
                        onResentments = onResentments,
                        isResentment = { viewModel.catalog.isResentmentChapter(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepAccordion(
    step: TreeNode,
    expanded: Boolean,
    currentId: Int?,
    count: Int,
    chapterCount: (Int) -> Int,
    onToggleStep: () -> Unit,
    onToggleChapter: (Int) -> Unit,
    isChapterExpanded: (Int) -> Boolean,
    visiblePoints: (TreeNode) -> List<TreeNode>,
    onSelect: (Int) -> Unit,
    onResentments: () -> Unit,
    isResentment: (TreeNode) -> Boolean
) {
    Column {
        AccordionHeader(
            title = step.displayTitle(),
            count = count,
            expanded = expanded,
            onClick = onToggleStep
        )
        AnimatedChildren(expanded && step.hasChildren) {
            step.children.forEach { chapter ->
                ChapterAccordion(
                    chapter = chapter,
                    expanded = isChapterExpanded(chapter.id),
                    currentId = currentId,
                    count = chapterCount(chapter.id),
                    pointCount = chapterCount,
                    onToggle = { onToggleChapter(chapter.id) },
                    visiblePoints = visiblePoints(chapter),
                    onSelect = onSelect,
                    showResentments = isResentment(chapter),
                    onResentments = onResentments
                )
            }
        }
    }
}

@Composable
private fun ChapterAccordion(
    chapter: TreeNode,
    expanded: Boolean,
    currentId: Int?,
    count: Int,
    pointCount: (Int) -> Int,
    onToggle: () -> Unit,
    visiblePoints: List<TreeNode>,
    onSelect: (Int) -> Unit,
    showResentments: Boolean,
    onResentments: () -> Unit
) {
    Column {
        AccordionHeader(
            title = chapter.name,
            count = count,
            expanded = expanded,
            onClick = onToggle
        )
        AnimatedChildren(expanded && (chapter.hasChildren || showResentments)) {
            if (showResentments) {
                JournalButton(JournalRu.openInventory, onResentments, filled = true)
            }
            visiblePoints.forEach { point ->
                LeafRow(
                    title = point.name,
                    count = pointCount(point.id),
                    highlighted = point.id == currentId,
                    onClick = { onSelect(point.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSelectedScreen(
    viewModel: JournalViewModel,
    onMenu: () -> Unit,
    onPickParent: () -> Unit,
    onAiHelp: () -> Unit,
    onAiAnalyze: (String) -> Unit,
    onResentments: () -> Unit,
    onEntries: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val path = state.path
    val node = path?.current
    val next = viewModel.nextPoint()
    val selectedTitle = when (node?.type) {
        NodeType.STEP -> JournalRu.stepSelected
        else -> JournalRu.pointSelected
    }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(2500)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(selectedTitle, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onMenu) },
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
                Text(node?.displayTitle().orEmpty(), style = MaterialTheme.typography.titleLarge, color = Forest)
                if (node?.type == NodeType.POINT) {
                    Text("${JournalRu.youAreIn} ${node.name}", color = Amber)
                }
                Text(JournalRu.thenWrite, color = Forest, style = MaterialTheme.typography.bodyLarge)
                if (node?.type == NodeType.POINT) {
                    JournalButton(JournalRu.aiHelp, onAiHelp)
                }
                if (state.writable) {
                    JournalEntryComposer(
                        state = state,
                        viewModel = viewModel
                    ) {
                        if (state.lastSaved != null) {
                            JournalButton(JournalRu.viewEntries, onEntries)
                            JournalButton(JournalRu.aiAnalyze, {
                                state.lastSaved?.id?.let(onAiAnalyze)
                            })
                            JournalButton(JournalRu.editThis, {
                                state.lastSaved?.let(viewModel::startEdit)
                            })
                        }
                    }
                }
                NoteView(
                    NoteIds.journal(node?.id ?: 0),
                    node?.description.orEmpty(),
                    node?.displayTitle().orEmpty()
                )
                if (viewModel.catalog.isResentmentChapter(path?.chapter)) {
                    JournalButton(JournalRu.openInventory, onResentments, filled = true)
                }
                if (next != null) {
                    JournalButton("${JournalRu.nextPoint}: ${next.name.take(42)}", {
                        viewModel.goNextPoint()
                    })
                }
                JournalButton(JournalRu.otherCategory, onPickParent)
                JournalButton(JournalRu.mainMenu, onMenu, filled = true)
            }
        }
    }
}
