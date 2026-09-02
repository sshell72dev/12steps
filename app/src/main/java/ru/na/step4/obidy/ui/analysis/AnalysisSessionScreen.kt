package ru.na.step4.obidy.ui.analysis



import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.ColumnScope

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.KeyboardActions

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.compose.material.icons.automirrored.outlined.ArrowForward

import androidx.compose.material.icons.automirrored.outlined.Send

import androidx.compose.material.icons.outlined.MoreVert

import androidx.compose.material.icons.outlined.PlayArrow

import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.DropdownMenu

import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.LinearProgressIndicator

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedButton

import androidx.compose.material3.OutlinedTextFieldDefaults

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.material3.TopAppBar

import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable

import androidx.compose.runtime.DisposableEffect

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.focus.focusProperties

import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.LocalFocusManager

import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle

import androidx.lifecycle.LifecycleEventObserver

import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.viewmodel.compose.viewModel

import ru.na.step4.obidy.Ru

import ru.na.step4.obidy.data.analysis.Prayer

import ru.na.step4.obidy.data.analysis.QaPair

import ru.na.step4.obidy.data.analysis.SessionScreen

import ru.na.step4.obidy.data.notes.NoteIds

import ru.na.step4.obidy.data.notes.NoteMode

import ru.na.step4.obidy.ui.AppNavIcon

import ru.na.step4.obidy.ui.components.AtmosphereBackground

import ru.na.step4.obidy.ui.components.NoteView

import ru.na.step4.obidy.ui.components.imeScaffoldContent

import ru.na.step4.obidy.ui.theme.Amber

import ru.na.step4.obidy.ui.theme.Forest

import ru.na.step4.obidy.ui.theme.Moss

import ru.na.step4.obidy.ui.theme.Sand

import ru.na.step4.obidy.ui.theme.SandDeep

import ru.na.steps12.voice.ui.LocalVoicePlugin
import ru.na.steps12.voice.ui.SpeakIconButton
import ru.na.steps12.voice.ui.VoiceOutlinedTextField



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun AnalysisSessionScreen(

    viewModel: AnalysisSessionViewModel,

    onLeave: () -> Unit

) {

    val screen by viewModel.screen.collectAsStateWithLifecycle()

    val saved by viewModel.saved.collectAsStateWithLifecycle()

    val aiViewModel: AnalysisAiReviewViewModel = viewModel(key = "ai-${viewModel.catalogId}")

    val aiState by aiViewModel.state.collectAsStateWithLifecycle()

    val reflectionViewModel: AnalysisReflectionViewModel = viewModel(key = "refl-${viewModel.catalogId}")

    val reflectionQuestion by reflectionViewModel.question.collectAsStateWithLifecycle()

    val draft by viewModel.draft.collectAsStateWithLifecycle()

    val answerNav by viewModel.answerNav.collectAsStateWithLifecycle()

    val reviewIndex by viewModel.reviewIndex.collectAsStateWithLifecycle()

    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current



    fun persistAndLeave() {

        viewModel.persistProgress(

            markActive = false,

            reflection = reflectionViewModel.snapshot(),

            leaving = true

        )

        onLeave()

    }



    fun persistNow(markActive: Boolean) {

        viewModel.persistProgress(

            markActive = markActive,

            reflection = reflectionViewModel.snapshot()

        )

    }



    BackHandler(onBack = ::persistAndLeave)



    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_STOP) persistNow(markActive = true)

        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }

    }



    LaunchedEffect(Unit) {

        viewModel.consumeReflection()?.let { reflectionViewModel.restore(it) }

        persistNow(markActive = true)

    }



    val done = screen as? SessionScreen.Done

    LaunchedEffect(done?.title, done?.answers) {

        if (done != null) aiViewModel.showCached(done.title, done.answers)

    }



    val title = reflectionQuestion?.title ?: when (val s = screen) {

        is SessionScreen.Preview -> s.title

        is SessionScreen.Question -> s.title

        is SessionScreen.Done -> s.title

        null -> Ru.sectionAnalysis

    }



    Scaffold(

        containerColor = Sand,

        topBar = {

            TopAppBar(

                title = {

                    Text(

                        title,

                        style = MaterialTheme.typography.titleLarge,

                        color = Forest,

                        maxLines = 2

                    )

                },

                navigationIcon = { AppNavIcon(onBack = ::persistAndLeave) },

                actions = {

                    if (screen !is SessionScreen.Done && reflectionQuestion == null) {

                        IconButton(onClick = { menuOpen = true }) {

                            Icon(

                                Icons.Outlined.MoreVert,

                                contentDescription = Ru.analysisMore,

                                tint = Forest

                            )

                        }

                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {

                            DropdownMenuItem(

                                text = { Text(Ru.analysisRestart) },

                                onClick = {

                                    menuOpen = false

                                    reflectionViewModel.reset()

                                    viewModel.restart()

                                }

                            )

                            DropdownMenuItem(

                                text = { Text(Ru.analysisExitConfirm) },

                                onClick = {

                                    menuOpen = false

                                    persistAndLeave()

                                }

                            )

                        }

                    }

                },

                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))

            )

        }

    ) { padding ->

        Box(Modifier.imeScaffoldContent(padding)) {

            AtmosphereBackground(Modifier.fillMaxSize())

            val reflection = reflectionQuestion

            val showAnswerNav = reflection == null &&

                (answerNav.canPrev || answerNav.canNext || answerNav.canResume)

            Column(Modifier.fillMaxSize()) {

                Box(Modifier.weight(1f).fillMaxWidth()) {

                    if (reflection != null) {

                        QuestionBody(

                            screen = reflection,

                            catalogId = "${viewModel.catalogId}-reflection",

                            initialDraft = draft,

                            onDraftChange = viewModel::setDraft,

                            onSubmit = { text ->

                                viewModel.setDraft("")

                                val pair = reflectionViewModel.submit(text)

                                if (pair != null) viewModel.appendReflection(listOf(pair))

                                persistNow(markActive = true)

                            },

                            onChoose = { _, _ -> }

                        )

                    } else when (val s = screen) {

                        is SessionScreen.Preview -> PreviewBody(

                            screen = s,

                            catalogId = viewModel.catalogId,

                            onBegin = viewModel::begin,

                            onPickCount = viewModel::pickMiniCount

                        )

                        is SessionScreen.Question -> QuestionBody(

                            screen = s,

                            catalogId = viewModel.catalogId,

                            initialDraft = draft,

                            onDraftChange = viewModel::setDraft,

                            onSubmit = viewModel::submit,

                            onChoose = viewModel::choose

                        )

                        is SessionScreen.Done -> DoneBody(

                            screen = s,

                            saved = saved,

                            streakDays = streakDays,

                            aiState = aiState,

                            onAi = {

                                aiViewModel.request(

                                    s.title,

                                    s.answers,

                                    force = aiState is AiReviewUi.Ready || aiState is AiReviewUi.Error

                                )

                            },

                            onShare = {

                                shareAnalysis(

                                    context,

                                    s.title,

                                    System.currentTimeMillis(),

                                    s.answers

                                )

                            },

                            onMenu = ::persistAndLeave,

                            onRestart = {

                                aiViewModel.reset()

                                reflectionViewModel.reset()

                                viewModel.restart()

                            },

                            onReflection = { items ->

                                reflectionViewModel.start(items)

                                persistNow(markActive = true)

                            }

                        )

                        null -> Column(Modifier.padding(20.dp)) {

                            Text(Ru.placeholderSoon, color = Forest)

                        }

                    }

                }

                if (showAnswerNav) {

                    AnswerNavBar(

                        nav = answerNav,

                        reviewing = reviewIndex != null,

                        onPrev = viewModel::goPrevAnswer,

                        onNext = viewModel::goNextAnswer,

                        onResume = viewModel::resumeLive

                    )

                }

            }

        }

    }

}



@Composable

private fun PreviewBody(

    screen: SessionScreen.Preview,

    catalogId: String,

    onBegin: () -> Unit,

    onPickCount: (Int) -> Unit

) {

    val isMiniPick = screen.countOptions.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {

        Column(

            modifier = Modifier

                .weight(1f)

                .fillMaxWidth()

                .verticalScroll(rememberScrollState())

                .padding(20.dp)

        ) {

            Text(

                if (isMiniPick) Ru.analysisMiniCount else Ru.analysisPreview,

                style = MaterialTheme.typography.labelMedium,

                color = Amber

            )

            Spacer(Modifier.height(12.dp))

            if (screen.blocks.isEmpty() && isMiniPick) {

                NoteView(NoteIds.ANALYSIS_MINI_HINT, Ru.analysisMiniHint, Ru.analysisMiniCount)

            }

            screen.blocks.forEachIndexed { index, block ->

                val text = buildString {

                    block.heading?.takeIf { it.isNotBlank() }?.let {

                        append(it)

                        append("\n\n")

                    }

                    append(block.lines.joinToString("\n"))

                }

                NoteView(

                    NoteIds.analysisPreview(catalogId, index),

                    text,

                    block.heading?.takeIf { it.isNotBlank() } ?: screen.title,

                    defaultMode = NoteMode.EXPANDED

                )

                Spacer(Modifier.height(10.dp))

            }

        }

        ActionBar {

            if (isMiniPick) {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(8.dp)

                ) {

                    screen.countOptions.forEach { n ->

                        val selected = screen.selectedCount == n

                        if (selected) {

                            Button(

                                onClick = { onPickCount(n) },

                                modifier = Modifier

                                    .weight(1f)

                                    .height(52.dp)

                                    .focusProperties { canFocus = false },

                                colors = ButtonDefaults.buttonColors(

                                    containerColor = Forest,

                                    contentColor = Sand

                                ),

                                shape = RoundedCornerShape(14.dp)

                            ) { Text(n.toString()) }

                        } else {

                            OutlinedButton(

                                onClick = { onPickCount(n) },

                                modifier = Modifier

                                    .weight(1f)

                                    .height(52.dp)

                                    .focusProperties { canFocus = false },

                                shape = RoundedCornerShape(14.dp)

                            ) { Text(n.toString(), color = Forest) }

                        }

                    }

                }

            }

            if (screen.canBegin) {

                Button(

                    onClick = onBegin,

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(52.dp)

                        .focusProperties { canFocus = false },

                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),

                    shape = RoundedCornerShape(14.dp)

                ) { Text(screen.primaryLabel) }

            }

        }

    }

}



@Composable
internal fun QuestionBody(
    screen: SessionScreen.Question,
    catalogId: String,
    onSubmit: (String) -> Unit,
    onChoose: (String, String) -> Unit,
    initialDraft: String = "",
    onDraftChange: (String) -> Unit = {}
) {
    var draft by remember(screen.question, screen.progressIndex) { mutableStateOf(initialDraft) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val voicePlugin = LocalVoicePlugin.current
    val composing = draft.isNotBlank()

    LaunchedEffect(screen.question, screen.progressIndex) {
        draft = initialDraft
        focus.clearFocus(force = true)
        keyboard?.hide()
        voicePlugin?.stopSpeaking()
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        onDraftChange("")
        focus.clearFocus(force = true)
        keyboard?.hide()
        onSubmit(text)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(if (composing && screen.allowText) 0.36f else 1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                Ru.analysisQuestionOf.format(screen.progressIndex, screen.progressTotal),
                style = MaterialTheme.typography.labelMedium,
                color = Amber
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    val total = screen.progressTotal.coerceAtLeast(1)
                    (screen.progressIndex / total.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Amber,
                trackColor = SandDeep,
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(16.dp))
            screen.prayer?.let { PrayerCard(catalogId, it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    screen.question,
                    style = MaterialTheme.typography.titleLarge,
                    color = Forest,
                    modifier = Modifier.weight(1f)
                )
                SpeakIconButton(
                    text = screen.question,
                    tint = Forest,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (screen.allowText) {
            VoiceOutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onDraftChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .then(if (composing) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 176.dp),
                minLines = if (composing) 10 else 4,
                maxLines = Int.MAX_VALUE,
                placeholder = { Text(Ru.analysisAnswerHint) },
                trailingIcon = {
                    PsychDayPickerIcon(
                        onInsert = { addition ->
                            val next = mergePsychIntoDraft(draft, addition)
                            draft = next
                            onDraftChange(next)
                        }
                    )
                },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!screen.hideSend) send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Forest,
                    unfocusedBorderColor = Moss.copy(alpha = 0.35f),
                    focusedContainerColor = Sand.copy(alpha = 0.7f),
                    unfocusedContainerColor = Sand.copy(alpha = 0.45f),
                    cursorColor = Forest
                )
            )
        }

        val showChoices = screen.choices.isNotEmpty() && !composing
        val showSend = screen.allowText && !screen.hideSend
        if (showChoices || showSend) {
            ActionBar {
                if (showChoices) {
                    screen.choices.forEach { choice ->
                        OutlinedButton(
                            onClick = { onChoose(choice.id, draft) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties { canFocus = false },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(choice.label, color = Forest) }
                    }
                }
                if (showSend) {
                    Button(
                        onClick = ::send,
                        enabled = draft.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusProperties { canFocus = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text(Ru.send)
                    }
                }
            }
        }
    }
}


@Composable

private fun AnswerNavBar(

    nav: AnswerNav,

    reviewing: Boolean,

    onPrev: () -> Unit,

    onNext: () -> Unit,

    onResume: () -> Unit

) {

    Row(

        modifier = Modifier

            .fillMaxWidth()

            .padding(horizontal = 12.dp, vertical = 4.dp),

        horizontalArrangement = Arrangement.Center,

        verticalAlignment = Alignment.CenterVertically

    ) {

        SubtleNavIcon(

            enabled = nav.canPrev,

            onClick = onPrev,

            image = Icons.AutoMirrored.Outlined.ArrowBack,

            description = Ru.analysisAnswerPrev

        )

        Spacer(Modifier.size(8.dp))

        SubtleNavIcon(

            enabled = nav.canNext,

            onClick = onNext,

            image = Icons.AutoMirrored.Outlined.ArrowForward,

            description = Ru.analysisAnswerNext

        )

        Spacer(Modifier.size(8.dp))

        SubtleNavIcon(

            enabled = nav.canResume,

            onClick = onResume,

            image = Icons.Outlined.PlayArrow,

            description = Ru.analysisAnswerResume,

            active = reviewing

        )

    }

}



@Composable

private fun SubtleNavIcon(

    enabled: Boolean,

    onClick: () -> Unit,

    image: ImageVector,

    description: String,

    active: Boolean = false

) {

    val tint = when {

        !enabled -> Moss.copy(alpha = 0.18f)

        active -> Amber.copy(alpha = 0.75f)

        else -> Moss.copy(alpha = 0.38f)

    }

    IconButton(

        onClick = onClick,

        enabled = enabled,

        modifier = Modifier

            .size(36.dp)

            .clip(CircleShape)

    ) {

        Icon(

            image,

            contentDescription = description,

            tint = tint,

            modifier = Modifier.size(20.dp)

        )

    }

}



@Composable

private fun ActionBar(content: @Composable ColumnScope.() -> Unit) {

    Column(

        modifier = Modifier

            .fillMaxWidth()

            .background(Sand.copy(alpha = 0.96f))

            .padding(horizontal = 20.dp, vertical = 12.dp),

        verticalArrangement = Arrangement.spacedBy(8.dp),

        content = content

    )

}



@Composable

private fun PrayerCard(catalogId: String, prayer: Prayer) {

    NoteView(

        NoteIds.analysisPrayer(catalogId, prayer.title.ifBlank { prayer.text.take(24) }),

        prayer.text,

        prayer.title.ifBlank { Ru.analysisEyebrow },

        defaultMode = NoteMode.EXPANDED

    )

    Spacer(Modifier.height(16.dp))

}



@Composable

private fun DoneBody(

    screen: SessionScreen.Done,

    saved: Boolean,

    streakDays: Int,

    aiState: AiReviewUi,

    onAi: () -> Unit,

    onShare: () -> Unit,

    onMenu: () -> Unit,

    onRestart: () -> Unit,

    onReflection: (List<String>) -> Unit

) {

    Column(

        modifier = Modifier

            .fillMaxSize()

            .verticalScroll(rememberScrollState())

            .padding(20.dp),

        verticalArrangement = Arrangement.spacedBy(10.dp)

    ) {

        Text(

            Ru.analysisDoneThanks,

            style = MaterialTheme.typography.titleLarge,

            color = Forest

        )

        if (streakDays > 0) {

            Text(

                "${Ru.analysisStreak} · $streakDays ${streakDayWord(streakDays)}",

                style = MaterialTheme.typography.titleMedium,

                color = Amber

            )

        }

        Text(

            if (saved) Ru.analysisSaved else Ru.analysisSave,

            style = MaterialTheme.typography.bodyMedium,

            color = if (saved) Amber else MaterialTheme.colorScheme.onSurfaceVariant

        )

        Button(

            onClick = onAi,

            enabled = aiState !is AiReviewUi.Loading,

            modifier = Modifier.fillMaxWidth().height(48.dp),

            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),

            shape = RoundedCornerShape(14.dp)

        ) { Text(aiReviewButtonLabel(aiState)) }

        if (aiState is AiReviewUi.Ready) {

            ReflectionActionButton(aiState.text, onReflection)

        }

        if (aiState !is AiReviewUi.Loading) {
            ListenAnswersButton(answers = screen.answers)
        }

        OutlinedButton(

            onClick = onShare,

            modifier = Modifier.fillMaxWidth().height(48.dp),

            shape = RoundedCornerShape(14.dp)

        ) { Text(Ru.analysisShare) }

        OutlinedButton(

            onClick = onMenu,

            modifier = Modifier.fillMaxWidth().height(48.dp),

            shape = RoundedCornerShape(14.dp)

        ) { Text(Ru.analysisToMenu) }

        TextButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {

            Text(Ru.analysisRestart, color = Forest)

        }

        AiReviewPanel(

            state = aiState,

            answers = screen.answers,

            onRetry = if (aiState is AiReviewUi.Ready || aiState is AiReviewUi.Error) onAi else null

        )

        Spacer(Modifier.height(8.dp))

        AnswersList(screen.answers)

    }

}



@Composable

fun AnswersList(answers: List<QaPair>) {

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Text(

            Ru.analysisAnswersTitle,

            style = MaterialTheme.typography.titleMedium,

            color = Forest

        )

        answers.forEach { pair ->

            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .clip(RoundedCornerShape(14.dp))

                    .background(SandDeep.copy(alpha = 0.72f))

                    .padding(12.dp)

            ) {

                Text(

                    pair.question,

                    style = MaterialTheme.typography.titleMedium,

                    color = Forest

                )

                Spacer(Modifier.height(4.dp))

                Text(

                    pair.answer,

                    style = MaterialTheme.typography.bodyMedium,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

            }

        }

    }

}



private fun streakDayWord(days: Int): String {

    val n = days % 100

    val n1 = days % 10

    return when {

        n in 11..14 -> Ru.lockDay5

        n1 == 1 -> Ru.lockDay1

        n1 in 2..4 -> Ru.lockDay2

        else -> Ru.lockDay5

    }

}

