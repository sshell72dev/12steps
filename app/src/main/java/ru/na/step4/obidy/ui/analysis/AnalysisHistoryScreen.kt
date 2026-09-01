package ru.na.step4.obidy.ui.analysis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.analysis.AnalysisAnswers
import ru.na.step4.obidy.data.analysis.AnalysisRecord
import ru.na.step4.obidy.data.analysis.QaPair
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisHistoryScreen(
    viewModel: AnalysisHistoryViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<AnalysisRecord?>(null) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Ru.analysisHistory,
                        style = MaterialTheme.typography.headlineMedium,
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
            AtmosphereBackground(Modifier.fillMaxSize())
            if (records.isEmpty()) {
                Text(
                    Ru.analysisEmptyHistory,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Forest,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        HistoryRow(
                            record = record,
                            onOpen = { onOpen(record.id) },
                            onDelete = { pendingDelete = record }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(Ru.analysisDeleteTitle) },
            text = { Text(Ru.analysisDeleteBody) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(record.id)
                    pendingDelete = null
                }) { Text(Ru.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(Ru.cancel) }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    record: AnalysisRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val answers = remember(record.answersJson) { AnalysisAnswers.decode(record.answersJson) }
    val preview = answers.firstOrNull()?.answer.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(record.title, style = MaterialTheme.typography.titleMedium, color = Forest)
            Text(
                AnalysisAnswers.formatDate(record.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = Amber
            )
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisDetailScreen(
    record: AnalysisRecord?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onAppendAnswers: (List<QaPair>) -> Unit
) {
    val context = LocalContext.current
    val answers = remember(record?.answersJson) {
        record?.let { AnalysisAnswers.decode(it.answersJson) }.orEmpty()
    }
    val aiViewModel: AnalysisAiReviewViewModel = viewModel(key = "ai-detail-${record?.id ?: 0L}")
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    val reflectionViewModel: AnalysisReflectionViewModel = viewModel(key = "refl-detail-${record?.id ?: 0L}")
    val reflectionQuestion by reflectionViewModel.question.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReflectionExit by remember { mutableStateOf(false) }

    fun requestBack() {
        if (reflectionViewModel.inProgress) confirmReflectionExit = true else onBack()
    }

    BackHandler(onBack = ::requestBack)

    LaunchedEffect(record?.id, record?.title, answers) {
        val current = record ?: return@LaunchedEffect
        if (answers.isNotEmpty()) aiViewModel.showCached(current.title, answers)
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            record?.let { AnalysisAnswers.formatDate(it.createdAt) }.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            reflectionQuestion?.title ?: record?.title ?: Ru.sectionAnalysis,
                            style = MaterialTheme.typography.titleLarge,
                            color = Forest,
                            maxLines = 2
                        )
                    }
                },
                navigationIcon = { AppNavIcon(onBack = ::requestBack) },
                actions = {
                    if (record != null) {
                        IconButton(onClick = {
                            shareAnalysis(context, record.title, record.createdAt, answers)
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = Ru.analysisShare, tint = Forest)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Forest)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.imeScaffoldContent(padding)
        ) {
            AtmosphereBackground(Modifier.fillMaxSize())
            val reflection = reflectionQuestion
            if (reflection != null && record != null) {
                QuestionBody(
                    screen = reflection,
                    catalogId = "${record.catalogId}-reflection",
                    onSubmit = { text ->
                        val pair = reflectionViewModel.submit(text)
                        if (pair != null) onAppendAnswers(listOf(pair))
                    },
                    onChoose = { _, _ -> }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                if (record == null) {
                    Text(Ru.analysisEmptyHistory, color = Forest)
                } else {
                    Button(
                        onClick = {
                            aiViewModel.request(
                                record.title,
                                answers,
                                force = aiState is AiReviewUi.Ready || aiState is AiReviewUi.Error
                            )
                        },
                        enabled = aiState !is AiReviewUi.Loading && answers.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(aiReviewButtonLabel(aiState)) }
                    val readyReview = aiState as? AiReviewUi.Ready
                    if (readyReview != null) {
                        Spacer(Modifier.height(10.dp))
                        ReflectionActionButton(readyReview.text, reflectionViewModel::start)
                    }
                    if (aiState !is AiReviewUi.Loading) {
                        Spacer(Modifier.height(10.dp))
                        ListenAnswersButton(answers = answers)
                    }
                    AiReviewPanel(
                        state = aiState,
                        answers = answers,
                        onRetry = if (aiState is AiReviewUi.Ready || aiState is AiReviewUi.Error) {
                            {
                                aiViewModel.request(record.title, answers, force = true)
                            }
                        } else {
                            null
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    AnswersList(answers)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        Ru.homeSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
        }
    }

    if (confirmReflectionExit) {
        AlertDialog(
            onDismissRequest = { confirmReflectionExit = false },
            title = { Text(Ru.analysisReflectionExitTitle) },
            text = { Text(Ru.analysisReflectionExitBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReflectionExit = false
                    reflectionViewModel.reset()
                }) { Text(Ru.analysisReflectionExitConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReflectionExit = false }) { Text(Ru.cancel) }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(Ru.analysisDeleteTitle) },
            text = { Text(Ru.analysisDeleteBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(Ru.delete) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(Ru.cancel) }
            }
        )
    }
}
