package ru.na.step4.obidy.ui.psych

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.MainActivity
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.psych.PsychLogic
import ru.na.step4.obidy.data.psych.PsychQa
import ru.na.step4.obidy.data.psych.PsychReminderWorker
import ru.na.step4.obidy.data.psych.PsychRu
import ru.na.step4.obidy.data.psych.PsychSettings
import ru.na.step4.obidy.data.psych.PsychTopic
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.components.isImeVisible
import ru.na.step4.obidy.ui.components.navigationBarsPaddingIfImeHidden
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.LocalVoicePlugin
import ru.na.steps12.voice.ui.SpeakIconButton
import ru.na.steps12.voice.ui.SpeakableText
import ru.na.steps12.voice.ui.VoiceOutlinedTextField


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PsychHostScreen(
    viewModel: PsychViewModel,
    onLeaveAppHome: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val postponed by viewModel.postponed.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voicePlugin = LocalVoicePlugin.current
    val psychOpenTick = (context as? MainActivity)?.psychOpenTick ?: 0
    LaunchedEffect(psychOpenTick) {
        val activity = context as? MainActivity ?: return@LaunchedEffect
        if (activity.consumePendingPsychOpen()) {
            viewModel.consumeNotificationOpen()
        }
    }

    LaunchedEffect(ui.speaking) {
        if (ui.speaking) {
            val text = viewModel.consumeSpeakable()
            if (text.isNotBlank()) {
                voicePlugin?.speak(text)
            }
        }
    }

    val onBack = {
        when (ui.page) {
            PsychPage.Hub, is PsychPage.Onboarding -> onLeaveAppHome()
            PsychPage.Settings, PsychPage.Record, is PsychPage.ViewPeriod,
            is PsychPage.Paywall, is PsychPage.SessionList -> viewModel.goHub()
            PsychPage.Profile, PsychPage.AiSettings, PsychPage.Topics, PsychPage.Reminders ->
                viewModel.goSettings()
            is PsychPage.TopicDetail -> viewModel.goTopics()
            else -> viewModel.goHub()
        }
    }
    BackHandler(onBack = onBack)

    val hideBar = ui.page is PsychPage.Onboarding || isImeVisible() ||
        (ui.waiting && ui.page !is PsychPage.Dialogue && ui.page !is PsychPage.Work)
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            PsychRu.eyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            Ru.sectionPsych,
                            style = MaterialTheme.typography.titleLarge,
                            color = Forest
                        )
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    IconButton(onClick = viewModel::goSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = PsychRu.settings,
                            tint = Forest
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        bottomBar = {
            if (!hideBar) {
                PsychBottomBar(
                    page = ui.page,
                    onRecord = viewModel::goRecord,
                    onView = { viewModel.openView(false) },
                    onSettings = viewModel::goSettings,
                    onMenu = viewModel::goHub
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            when (val page = ui.page) {
                is PsychPage.Onboarding -> OnboardingBody(page, viewModel)
                PsychPage.Hub -> HubBody(viewModel)
                PsychPage.Record -> RecordBody(viewModel)
                is PsychPage.TopicPick -> TopicPickBody(page, topics, viewModel)
                is PsychPage.Dialogue -> DialogueBody(page, ui, viewModel, onOpenProfile)
                is PsychPage.Result -> ResultBody(page, ui, viewModel)
                is PsychPage.Work -> WorkBody(page, ui, viewModel)
                is PsychPage.Done -> DoneBody(page, viewModel)
                is PsychPage.ViewPeriod -> ViewBody(page, viewModel)
                PsychPage.Settings -> SettingsBody(viewModel, onOpenProfile)
                PsychPage.Profile -> ProfileBody(viewModel)
                PsychPage.AiSettings -> AiSettingsBody(viewModel)
                PsychPage.Topics -> TopicsBody(topics, ui.topicSnippets, viewModel)
                is PsychPage.TopicDetail -> TopicDetailBody(page, viewModel)
                PsychPage.Reminders -> RemindersBody(viewModel)
                is PsychPage.Paywall -> PaywallBody(viewModel)
                is PsychPage.SessionList -> SessionListBody(
                    postponed = page.postponed,
                    sessions = if (page.postponed) postponed else completed,
                    viewModel = viewModel
                )
                is PsychPage.Idle -> IdleBody(page, viewModel)
                is PsychPage.Review -> ReviewBody(page, viewModel, onOpenProfile)
            }
            if (ui.waiting && ui.page !is PsychPage.Dialogue && ui.page !is PsychPage.Work) {
                WaitOverlay(ui)
            }
        }
    }

    ui.error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text(Ru.confirm) }
            },
            title = { Text(Ru.sectionPsych) },
            text = { Text(msg) }
        )
    }
}

@Composable
private fun PsychBottomBar(
    page: PsychPage,
    onRecord: () -> Unit,
    onView: () -> Unit,
    onSettings: () -> Unit,
    onMenu: () -> Unit
) {
    NavigationBar(
        containerColor = Sand.copy(alpha = 0.96f),
        modifier = Modifier.navigationBarsPaddingIfImeHidden()
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Forest,
            selectedTextColor = Forest,
            indicatorColor = SandDeep,
            unselectedIconColor = Moss,
            unselectedTextColor = Moss
        )
        NavigationBarItem(
            selected = page is PsychPage.Record || page is PsychPage.Dialogue || page is PsychPage.TopicPick,
            onClick = onRecord,
            icon = { Icon(Icons.Outlined.EditNote, contentDescription = PsychRu.record) },
            label = { Text(PsychRu.record, maxLines = 1) },
            colors = colors
        )
        NavigationBarItem(
            selected = page is PsychPage.ViewPeriod,
            onClick = onView,
            icon = { Icon(Icons.Outlined.Visibility, contentDescription = PsychRu.view) },
            label = { Text(PsychRu.view, maxLines = 1) },
            colors = colors
        )
        NavigationBarItem(
            selected = page is PsychPage.Settings || page is PsychPage.Profile ||
                page is PsychPage.AiSettings || page is PsychPage.Topics || page is PsychPage.Reminders,
            onClick = onSettings,
            icon = { Icon(Icons.Outlined.Settings, contentDescription = PsychRu.settings) },
            label = { Text(PsychRu.settings, maxLines = 1) },
            colors = colors
        )
        NavigationBarItem(
            selected = page is PsychPage.Hub,
            onClick = onMenu,
            icon = { Icon(Icons.Outlined.Home, contentDescription = PsychRu.mainMenu) },
            label = { Text(PsychRu.mainMenu, maxLines = 1) },
            colors = colors
        )
    }
}

@Composable
private fun WaitOverlay(ui: PsychUi) {
    val title = PsychRu.waitingTitle(ui.waitKind)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand.copy(alpha = 0.92f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SandDeep)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = Forest,
                strokeWidth = 3.dp
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Forest,
                textAlign = TextAlign.Center
            )
            Text(
                PsychRu.voiceHint,
                style = MaterialTheme.typography.bodyMedium,
                color = Moss,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HubBody(vm: PsychViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val streakDays by vm.streakDays.collectAsStateWithLifecycle()
        val streakLabel = remember(streakDays) { vm.streakLabel() }
        if (streakLabel != null) {
            Text(
                streakLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Amber
            )
        }
        NoteView(NoteIds.PSYCH_INTRO, PsychRu.intro, Ru.sectionPsych)
        NoteView(NoteIds.PSYCH_DISCLAIMER, PsychRu.disclaimer, PsychRu.disclaimer)
        MenuBtn(PsychRu.record, onClick = vm::goRecord)
        MenuBtn(PsychRu.reminders, onClick = vm::goReminders)
        MenuBtn(PsychRu.topics, onClick = vm::goTopics)
        MenuBtn(PsychRu.view, onClick = { vm.openView(false) })
        MenuBtn(PsychRu.postponed, onClick = { vm.goSessions(true) })
        MenuBtn(PsychRu.completed, onClick = { vm.goSessions(false) })
        MenuBtn(PsychRu.settings, onClick = vm::goSettings)
    }
}

@Composable
private fun OnboardingBody(page: PsychPage.Onboarding, vm: PsychViewModel) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(PsychRu.welcome, style = MaterialTheme.typography.bodyLarge, color = Forest)
        page.hint?.let { Text(it, color = Amber, style = MaterialTheme.typography.bodyMedium) }
        Text(PsychRu.askName, style = MaterialTheme.typography.titleMedium, color = Forest)
        PsychField(name, { name = it }, PsychRu.name)
        PrimaryBtn(Ru.save) { vm.submitOnboardingName(name) }
        OutlinedButton(
            onClick = vm::skipOnboarding,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text(PsychRu.skip, color = Forest) }
    }
}

@Composable
private fun RecordBody(vm: PsychViewModel) {
    var text by remember { mutableStateOf("") }
    val typing = isImeVisible()
    val composing = typing || text.isNotBlank()
    PsychWriteColumn(
        text = text,
        onChange = { text = it },
        hint = PsychRu.describe,
        composing = composing,
        showField = true,
        header = {
            if (!typing) {
                Text(PsychRu.describe, style = MaterialTheme.typography.titleLarge, color = Forest)
            }
        },
        footer = {
            ActionBar {
                PrimaryBtn(PsychRu.send, enabled = text.isNotBlank()) { vm.submitSituation(text) }
            }
        }
    )
}

@Composable
private fun TopicPickBody(
    page: PsychPage.TopicPick,
    topics: List<PsychTopic>,
    vm: PsychViewModel
) {
    var newName by remember { mutableStateOf("") }
    val shown = if (page.showAll) topics else topics.take(3)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(PsychRu.topicSaved, style = MaterialTheme.typography.bodyLarge, color = Forest)
        Text(PsychRu.topicPickHint, style = MaterialTheme.typography.bodyMedium, color = Moss)
        if (page.selectedIds.isNotEmpty()) {
            Text(
                PsychRu.selectedCount.format(page.selectedIds.size),
                style = MaterialTheme.typography.labelMedium,
                color = Amber
            )
        }
        shown.forEach { topic ->
            val selected = topic.id in page.selectedIds
            val snippet = page.snippets[topic.id].orEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) SandDeep else SandDeep.copy(alpha = 0.55f)
                    )
                    .clickable { vm.toggleTopicSelection(topic.id) }
                    .padding(12.dp)
            ) {
                Text(
                    if (selected) "✓ ${topic.name}" else topic.name,
                    color = Forest,
                    style = MaterialTheme.typography.titleMedium
                )
                if (snippet.isNotBlank()) {
                    Text(
                        snippet,
                        color = Moss,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }
        if (!page.showAll && topics.size > 3) {
            MenuBtn(PsychRu.moreTopics) { vm.showAllTopics(page.situationId) }
        }
        PrimaryBtn(
            PsychRu.confirmTopics,
            enabled = page.selectedIds.isNotEmpty()
        ) { vm.confirmSelectedTopics() }
        MenuBtn(PsychRu.noTopic) { vm.pickTopic(page.situationId, null, false) }
        MenuBtn(PsychRu.noHistory) { vm.pickTopic(page.situationId, null, true) }
        PsychField(newName, { newName = it }, PsychRu.newTopicHint)
        MenuBtn(PsychRu.addTopic) {
            vm.addTopicAndPick(page.situationId, newName)
            newName = ""
        }
    }
}

@Composable
private fun DialogueBody(
    page: PsychPage.Dialogue,
    ui: PsychUi,
    vm: PsychViewModel,
    onOpenProfile: () -> Unit
) {
    var draft by remember(page.question) { mutableStateOf("") }
    val waitingNext = ui.waiting
    val composing = !waitingNext && (draft.isNotBlank() || isImeVisible())
    val questionReady = page.question.isNotBlank()
    PsychWriteColumn(
        text = draft,
        onChange = { draft = it },
        hint = Ru.analysisAnswerHint,
        composing = composing,
        showField = questionReady && !waitingNext,
        header = {
            CollapsedRecordBlock(page.situation.text, forceCollapsed = waitingNext)
            if (!composing) {
                page.answers.forEach { qa ->
                    QaCard(qa)
                }
            }
            if (vm.isAdmin && page.prompt.isNotBlank() && !composing) {
                ru.na.step4.obidy.ui.components.AdminPromptBlock(
                    page.prompt,
                    origin = ru.na.step4.obidy.ui.components.PromptOrigin.psych("dialogue_question")
                )
            }
            QuestionBlock(
                question = page.question,
                waiting = waitingNext,
                waitingTitle = PsychRu.waitingTitle(ui.waitKind)
            )
            if (!composing) {
                ui.quotaLine?.let {
                    Text(it, color = Amber, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        footer = {
            var moreOpen by remember { mutableStateOf(false) }
            ActionBar {
                if (questionReady && !waitingNext) {
                    PrimaryBtn(PsychRu.send, enabled = draft.isNotBlank()) {
                        vm.answerDialogue(draft)
                    }
                }
                if (!moreOpen) {
                    MenuBtn(PsychRu.furtherActions) { moreOpen = true }
                } else {
                    MenuBtn(PsychRu.analyze) { vm.analyze() }
                    MenuBtn(PsychRu.recommend) { vm.recommend() }
                    MenuBtn(PsychRu.postpone) { vm.postpone() }
                    MenuBtn(PsychRu.work) { vm.startWork() }
                    if (vm.settings.hasEmptyProfileField()) {
                        MenuBtn(PsychRu.fillProfile) { onOpenProfile() }
                    }
                }
            }
        }
    )
}

@Composable
private fun ResultBody(page: PsychPage.Result, ui: PsychUi, vm: PsychViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CollapsedRecordBlock(page.situation.text)
        if (vm.isAdmin && page.prompt.isNotBlank()) {
            ru.na.step4.obidy.ui.components.AdminPromptBlock(
                page.prompt,
                origin = ru.na.step4.obidy.ui.components.PromptOrigin.psych(page.kind)
            )
        }
        ReadableText(page.text)
        if (page.teaser) {
            Text(PsychRu.teaserHint, color = Amber, style = MaterialTheme.typography.bodyMedium)
            PrimaryBtn(PsychRu.readMore) { vm.readMore() }
        }
        ui.quotaLine?.let { Text(it, color = Amber, style = MaterialTheme.typography.bodySmall) }
        ui.upsell?.let { Text(it, color = Forest, style = MaterialTheme.typography.bodySmall) }
        if (page.kind != "recommend") {
            MenuBtn(PsychRu.recommend) { vm.recommend() }
        }
        if (page.kind != "analyze") {
            MenuBtn(PsychRu.analyze) { vm.analyze() }
        }
        MenuBtn(PsychRu.share) { sharePlain(context, vm.shareText().orEmpty()) }
        MenuBtn(PsychRu.work) { vm.startWork() }
        MenuBtn(PsychRu.mainMenu) { vm.goHub() }
    }
}

@Composable
private fun WorkBody(page: PsychPage.Work, ui: PsychUi, vm: PsychViewModel) {
    var draft by remember(page.index) { mutableStateOf("") }
    val question = page.questions.getOrElse(page.index) { "" }
    val waitingNext = ui.waiting || (question.isBlank() && ui.error == null)
    val composing = !waitingNext && (draft.isNotBlank() || isImeVisible())
    val questionReady = question.isNotBlank()
    PsychWriteColumn(
        text = draft,
        onChange = { draft = it },
        hint = Ru.analysisAnswerHint,
        composing = composing,
        showField = questionReady && !waitingNext,
        header = {
            CollapsedRecordBlock(page.situation.text, forceCollapsed = waitingNext)
            Text(
                Ru.analysisQuestionOf.format(page.index + 1, page.questions.size.coerceAtLeast(page.index + 1)),
                color = Amber,
                style = MaterialTheme.typography.labelMedium
            )
            if (vm.isAdmin && page.prompt.isNotBlank() && !composing) {
                ru.na.step4.obidy.ui.components.AdminPromptBlock(
                    page.prompt,
                    origin = ru.na.step4.obidy.ui.components.PromptOrigin.psych(
                        if (page.answers.isEmpty()) "questions" else "questions_next"
                    )
                )
            }
            QuestionBlock(
                question = question,
                waiting = waitingNext,
                waitingTitle = PsychRu.waitingTitle(ui.waitKind)
            )
        },
        footer = {
            var moreOpen by remember { mutableStateOf(false) }
            ActionBar {
                if (questionReady && !waitingNext) {
                    PrimaryBtn(PsychRu.send, enabled = draft.isNotBlank()) {
                        vm.answerWork(draft)
                    }
                }
                if (!moreOpen) {
                    MenuBtn(PsychRu.furtherActions) { moreOpen = true }
                } else {
                    MenuBtn(PsychRu.analyze) { vm.analyze() }
                    MenuBtn(PsychRu.recommend) { vm.recommend() }
                    MenuBtn(PsychRu.postpone) { vm.postpone() }
                    MenuBtn(PsychRu.finish) { vm.finishNow() }
                }
            }
        }
    )
}

@Composable
private fun DoneBody(page: PsychPage.Done, vm: PsychViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CollapsedRecordBlock(page.situation.text)
        page.answers.forEach { QaCard(it) }
        PrimaryBtn(PsychRu.assistant) { vm.assistant() }
        MenuBtn(PsychRu.work) { vm.startWork() }
        MenuBtn(PsychRu.share) { sharePlain(context, vm.shareText().orEmpty()) }
        MenuBtn(PsychRu.mainMenu) { vm.goHub() }
    }
}

@Composable
private fun ViewBody(page: PsychPage.ViewPeriod, vm: PsychViewModel) {
    var date by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !page.week,
                onClick = { vm.openView(false) },
                label = { Text(PsychRu.day) },
                colors = chipColors()
            )
            FilterChip(
                selected = page.week,
                onClick = { vm.openView(true) },
                label = { Text(PsychRu.week) },
                colors = chipColors()
            )
        }
        if (!page.week) {
            PsychField(date, { date = it }, PsychRu.pickDate, minLines = 1)
            MenuBtn(PsychRu.today) { vm.openView(false) }
            MenuBtn(Ru.confirm) {
                val parsed = PsychLogic.parseDateInput(date, System.currentTimeMillis(), vm.settings.utcOffsetMinutes)
                if (parsed != null) vm.openView(false, parsed)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !page.asOneText,
                onClick = { vm.setViewAsOneText(false) },
                label = { Text(PsychRu.oneByOne) },
                colors = chipColors()
            )
            FilterChip(
                selected = page.asOneText,
                onClick = { vm.setViewAsOneText(true) },
                label = { Text(PsychRu.copyAll) },
                colors = chipColors()
            )
        }
        Text(
            PsychRu.viewModeHint,
            color = Moss,
            style = MaterialTheme.typography.bodySmall
        )
        if (page.items.isEmpty()) {
            Text(PsychRu.emptyView, color = Moss)
        } else if (page.asOneText) {
            val allText = page.items.joinToString("\n\n") { item ->
                PsychLogic.shareText(
                    item.situation.text,
                    item.answers,
                    item.situation.createdAt,
                    vm.settings.utcOffsetMinutes
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        PsychRu.countRecords.format(page.items.size),
                        modifier = Modifier.weight(1f),
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (allText.isNotBlank()) {
                        SpeakIconButton(text = allText, tint = Forest)
                    }
                }
                Text(
                    allText,
                    color = Forest,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            MenuBtn(PsychRu.share) { sharePlain(context, allText) }
        } else {
            var index by remember(page.from, page.to, page.items.size) { mutableIntStateOf(0) }
            val safeIndex = index.coerceIn(0, page.items.lastIndex)
            val item = page.items[safeIndex]
            Text(
                PsychRu.viewIndex.format(safeIndex + 1, page.items.size),
                color = Amber,
                style = MaterialTheme.typography.labelMedium
            )
            ViewSituationCard(
                item = item,
                onOpen = { vm.openSituationReview(item.situation.id, item.session?.id) }
            )
            if (page.items.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { index = (safeIndex - 1).coerceAtLeast(0) },
                        enabled = safeIndex > 0,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(PsychRu.prevSituation, color = Forest) }
                    OutlinedButton(
                        onClick = { index = (safeIndex + 1).coerceAtMost(page.items.lastIndex) },
                        enabled = safeIndex < page.items.lastIndex,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(PsychRu.nextSituation, color = Forest) }
                }
            }
        }
    }
}

@Composable
private fun ViewSituationCard(item: ViewItem, onOpen: () -> Unit) {
    val speakText = viewItemSpeakText(item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .clickable(onClick = onOpen)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.timeLabel,
                modifier = Modifier.weight(1f),
                color = Amber,
                style = MaterialTheme.typography.labelMedium
            )
            if (speakText.isNotBlank()) {
                SpeakIconButton(text = speakText, tint = Forest)
            }
        }
        Text(
            item.situation.text,
            color = Forest,
            style = MaterialTheme.typography.bodyMedium
        )
        item.answers.forEach { qa ->
            Spacer(Modifier.height(8.dp))
            Text(qa.question, color = Forest, style = MaterialTheme.typography.titleSmall)
            Text(qa.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsBody(vm: PsychViewModel, onOpenProfile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MenuBtn(PsychRu.profile) { onOpenProfile() }
        MenuBtn(PsychRu.topics) { vm.goTopics() }
        MenuBtn(PsychRu.aiSettings) { vm.goAi() }
        MenuBtn(PsychRu.reminders) { vm.goReminders() }
        MenuBtn(PsychRu.paywallTitle) { vm.goPaywall() }
    }
}

@Composable
private fun ProfileBody(vm: PsychViewModel) {
    val s = vm.settings
    var name by remember { mutableStateOf(s.name) }
    var birth by remember { mutableStateOf(s.birthYear) }
    var place by remember { mutableStateOf(s.location) }
    var about by remember { mutableStateOf(s.aboutMe) }
    var offset by remember { mutableStateOf(s.utcOffsetMinutes.toString()) }
    var custom by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PsychField(name, { name = it }, PsychRu.name, 1)
        PsychField(birth, { birth = it }, PsychRu.birth, 1)
        PsychField(place, { place = it }, PsychRu.place, 1)
        Text(PsychRu.program, color = Forest, style = MaterialTheme.typography.titleMedium)
        PsychSettings.PROGRAMS.forEach { p ->
            FilterChip(
                selected = s.recoveryProgram == p,
                onClick = { vm.saveProfileField("program", p) },
                label = { Text(p) },
                colors = chipColors()
            )
        }
        PsychField(custom, { custom = it }, PsychRu.customProgram, 1)
        MenuBtn(PsychRu.customProgram) { vm.saveProfileField("program", custom) }
        MenuBtn(PsychRu.skip) { vm.saveProfileField("program", "") }
        PsychField(about, { about = it }, PsychRu.about, 4)
        PsychField(offset, { offset = it }, PsychRu.timezone, 1)
        Text(PsychRu.offsetHint, color = Moss, style = MaterialTheme.typography.bodySmall)
        PrimaryBtn(PsychRu.saveName) {
            vm.saveProfileField("name", name)
            vm.saveProfileField("birth", birth)
            vm.saveProfileField("location", place)
            vm.saveProfileField("about", about)
            vm.saveProfileField("offset", offset)
            vm.goSettings()
        }
    }
}

@Composable
private fun AiSettingsBody(vm: PsychViewModel) {
    val s = vm.settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(PsychRu.personality, style = MaterialTheme.typography.titleMedium, color = Forest)
        SwitchRow(
            if (s.personalityCollectEnabled) PsychRu.personalityOn else PsychRu.personalityOff,
            s.personalityCollectEnabled
        ) { vm.setPersonalityCollect(it) }
        var portrait by remember(s.myPersonality) { mutableStateOf(s.myPersonality) }
        PsychField(portrait, { portrait = it }, PsychRu.personalityEdit, 5)
        MenuBtn(Ru.save) { vm.saveProfileField("personality", portrait) }

        Text(PsychRu.format, style = MaterialTheme.typography.titleMedium, color = Forest)
        FilterChip(
            selected = s.aiResponseVariant == "compact",
            onClick = { vm.setAiOption("variant", "compact") },
            label = { Text(PsychRu.compact) },
            colors = chipColors()
        )
        FilterChip(
            selected = s.aiResponseVariant == "expanded",
            onClick = { vm.setAiOption("variant", "expanded") },
            label = { Text("${PsychRu.expanded} ⭐") },
            colors = chipColors()
        )
        Text(PsychRu.style, style = MaterialTheme.typography.titleMedium, color = Forest)
        FilterChip(
            selected = s.aiResponseStyle == "neutral",
            onClick = { vm.setAiOption("style", "neutral") },
            label = { Text(PsychRu.neutral) },
            colors = chipColors()
        )
        FilterChip(
            selected = s.aiResponseStyle == "critical",
            onClick = { vm.setAiOption("style", "critical") },
            label = { Text("${PsychRu.critical} ⭐") },
            colors = chipColors()
        )
        Text(PsychRu.workQs, style = MaterialTheme.typography.titleMedium, color = Forest)
        FilterChip(
            selected = s.workQuestionDifficulty == "simple",
            onClick = { vm.setAiOption("diff", "simple") },
            label = { Text(PsychRu.simpleQ) },
            colors = chipColors()
        )
        FilterChip(
            selected = s.workQuestionDifficulty == "hard",
            onClick = { vm.setAiOption("diff", "hard") },
            label = { Text("${PsychRu.hardQ} ⭐") },
            colors = chipColors()
        )
        FilterChip(
            selected = s.workQuestionLength == "short",
            onClick = { vm.setAiOption("len", "short") },
            label = { Text(PsychRu.shortQ) },
            colors = chipColors()
        )
        FilterChip(
            selected = s.workQuestionLength == "long",
            onClick = { vm.setAiOption("len", "long") },
            label = { Text("${PsychRu.longQ} ⭐") },
            colors = chipColors()
        )
        Text(PsychRu.questionLimits, style = MaterialTheme.typography.titleMedium, color = Forest)
        Text(PsychRu.questionLimitsHint, color = Moss, style = MaterialTheme.typography.bodySmall)
        var dialogueExtra by remember(s.dialogueExtraLimit) {
            mutableStateOf(s.dialogueExtraLimit.toString())
        }
        var workCount by remember(s.workQuestionLimit) {
            mutableStateOf(s.workQuestionLimit.toString())
        }
        PsychField(dialogueExtra, { dialogueExtra = it }, PsychRu.dialogueExtraQs, 1)
        PsychField(workCount, { workCount = it }, PsychRu.workQuestionCount, 1)
        MenuBtn(Ru.save) {
            vm.setQuestionLimits(
                dialogueExtra.toIntOrNull(),
                workCount.toIntOrNull()
            )
        }
        SwitchRow(PsychRu.voiceOn, s.voiceEnabled, vm::setVoiceEnabled)
    }
}

@Composable
private fun TopicsBody(
    topics: List<PsychTopic>,
    snippets: Map<Long, String>,
    vm: PsychViewModel
) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SwitchRow(
            if (vm.settings.topicsEnabled) PsychRu.topicsOn else PsychRu.topicsOff,
            vm.settings.topicsEnabled,
            vm::setTopicsEnabled
        )
        PsychField(name, { name = it }, PsychRu.newTopicHint, 1)
        MenuBtn(PsychRu.addTopic) { vm.createTopic(name); name = "" }
        topics.forEach { topic ->
            val snippet = snippets[topic.id].orEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .clickable { vm.openTopic(topic) }
                    .padding(12.dp)
            ) {
                Text(topic.name, color = Forest, style = MaterialTheme.typography.titleMedium)
                Text("×${topic.useCount}", color = Moss, style = MaterialTheme.typography.bodySmall)
                if (snippet.isNotBlank()) {
                    Text(
                        snippet,
                        color = Moss,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicDetailBody(page: PsychPage.TopicDetail, vm: PsychViewModel) {
    val topic = page.topic
    var name by remember(topic.id) { mutableStateOf(topic.name) }
    var summary by remember(topic.id, topic.summaryText) { mutableStateOf(topic.summaryText) }
    var confirm by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PsychField(name, { name = it }, PsychRu.topicName, 1)
        MenuBtn(Ru.save) { vm.renameTopic(topic.id, name) }
        Text(PsychRu.topicMemory, color = Forest, style = MaterialTheme.typography.titleMedium)
        PsychField(summary, { summary = it }, PsychRu.topicMemory, 6)
        MenuBtn(Ru.save) { vm.saveTopicSummary(topic.id, summary) }
        Text(PsychRu.topicChronology, color = Forest, style = MaterialTheme.typography.titleMedium)
        if (page.stories.isEmpty()) {
            Text(PsychRu.topicEmptyChronology, color = Moss, style = MaterialTheme.typography.bodyMedium)
        } else {
            page.stories.forEach { story ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SandDeep.copy(alpha = 0.72f))
                        .clickable {
                            story.sessionId?.let { vm.openSession(it) }
                        }
                        .padding(12.dp)
                ) {
                    Text(
                        PsychLogic.formatLocal(story.createdAt, vm.settings.utcOffsetMinutes),
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        story.summary.ifBlank { PsychRu.none },
                        color = Forest,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        MenuBtn(Ru.delete) { confirm = true }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(PsychRu.deleteTopic) },
            text = { Text(PsychRu.deleteTopicBody) },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.deleteTopic(topic.id) }) { Text(Ru.delete) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(Ru.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersBody(vm: PsychViewModel) {
    val s = vm.settings
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hours by remember { mutableStateOf(s.reminderIntervalHours.toString()) }
    var quietStart by remember { mutableStateOf(s.quietStartHour.toString()) }
    var quietEnd by remember { mutableStateOf(s.quietEndHour.toString()) }
    var offset by remember { mutableStateOf(s.utcOffsetMinutes.toString()) }
    var canPost by remember { mutableStateOf(PsychReminderWorker.canPost(context)) }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        canPost = granted || PsychReminderWorker.canPost(context)
        if (granted) {
            PsychReminderWorker.notify(context, PsychRu.reminderFallback)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                canPost = PsychReminderWorker.canPost(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        PsychReminderWorker.schedule(context)
        vm.onRemindersShown()
    }
    fun requestNotifyIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !canPost) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun openSystemNotifySettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(PsychRu.reminderHow, color = Forest, style = MaterialTheme.typography.bodyLarge)
        Text(PsychRu.reminderWhere, color = Moss, style = MaterialTheme.typography.bodyMedium)
        if (!canPost) {
            Text(PsychRu.reminderPermissionOff, color = Amber, style = MaterialTheme.typography.bodyMedium)
            MenuBtn(PsychRu.reminderOpenSettings) { openSystemNotifySettings() }
        }
        SwitchRow(
            if (s.reminderEnabled) PsychRu.reminderOn else PsychRu.reminderOff,
            s.reminderEnabled
        ) { on ->
            if (on) requestNotifyIfNeeded()
            vm.enableReminders(on)
            if (on) {
                PsychReminderWorker.schedule(context, replace = true)
                if (canPost) {
                    PsychReminderWorker.notify(context, PsychRu.reminderFallback)
                }
            } else {
                PsychReminderWorker.cancel(context)
            }
        }
        Text(
            if (!s.reminderEnabled) {
                PsychRu.reminderNextOff
            } else {
                val next = s.nextReminderAt
                val whenLabel = if (next > 0L) {
                    PsychLogic.formatLocal(next, s.utcOffsetMinutes)
                } else {
                    PsychRu.intervalHours
                }
                PsychRu.reminderNext.format(whenLabel)
            },
            color = Forest,
            style = MaterialTheme.typography.titleSmall
        )
        Text(PsychRu.intervalHours, color = Moss, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = s.reminderIntervalHours == 6,
                onClick = {
                    hours = "6"
                    vm.setReminderHours(6)
                },
                label = { Text(PsychRu.interval6) },
                colors = chipColors()
            )
            FilterChip(
                selected = s.reminderIntervalHours == 12,
                onClick = {
                    hours = "12"
                    vm.setReminderHours(12)
                },
                label = { Text(PsychRu.interval12) },
                colors = chipColors()
            )
            FilterChip(
                selected = s.reminderIntervalHours == 24,
                onClick = {
                    hours = "24"
                    vm.setReminderHours(24)
                },
                label = { Text(PsychRu.interval24) },
                colors = chipColors()
            )
        }
        PsychField(hours, { hours = it }, PsychRu.intervalHours, 1)
        MenuBtn(Ru.save) {
            vm.setReminderHours(hours.toIntOrNull() ?: s.reminderIntervalHours)
            if (s.reminderEnabled) PsychReminderWorker.schedule(context, replace = true)
        }
        PsychField(quietStart, { quietStart = it }, PsychRu.quietStart, 1)
        PsychField(quietEnd, { quietEnd = it }, PsychRu.quietEnd, 1)
        Text(
            "${PsychRu.quietHours}: ${s.quietStartHour}:00–${s.quietEndHour}:00",
            color = Moss,
            style = MaterialTheme.typography.bodySmall
        )
        MenuBtn(Ru.save) {
            vm.setQuietHours(
                quietStart.toIntOrNull() ?: s.quietStartHour,
                quietEnd.toIntOrNull() ?: s.quietEndHour
            )
        }
        PsychField(offset, { offset = it }, PsychRu.timezone, 1)
        Text(PsychRu.offsetHint, color = Moss, style = MaterialTheme.typography.bodySmall)
        MenuBtn(Ru.save) { vm.saveProfileField("offset", offset) }
        PrimaryBtn(PsychRu.reminderTest) {
            if (!canPost) {
                requestNotifyIfNeeded()
                if (Build.VERSION.SDK_INT < 33) openSystemNotifySettings()
            } else {
                PsychReminderWorker.notify(context, PsychRu.reminderFallback)
            }
        }
    }
}

@Composable
private fun PaywallBody(vm: PsychViewModel) {
    val context = LocalContext.current
    val pay by vm.premiumPay.collectAsStateWithLifecycle()
    var priceRub by remember { mutableStateOf<String?>(null) }
    var paymentsEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cfg = withContext(Dispatchers.IO) {
            ru.na.step4.obidy.data.ai.AppConfigClient.fetch()
        }
        priceRub = cfg.premiumPriceRub
        paymentsEnabled = cfg.paymentsEnabled
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity = context as? ru.na.step4.obidy.MainActivity
    val returnTick = activity?.premiumReturnTick ?: 0
    LaunchedEffect(returnTick) {
        if (returnTick > 0) vm.onPremiumReturn()
    }
    DisposableEffect(lifecycleOwner, pay.awaitingReturn) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && pay.awaitingReturn) {
                vm.onPremiumReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val speakText = buildString {
            appendLine(PsychRu.paywallTitle)
            appendLine()
            appendLine(PsychRu.paywallBody)
            appendLine()
            append(PsychRu.upsell)
            priceRub?.let { price ->
                appendLine()
                appendLine()
                append(PsychRu.paywallPrice.format(price))
            }
            appendLine()
            appendLine()
            append(PsychRu.paywallHint)
            pay.message?.let { msg ->
                appendLine()
                appendLine()
                append(msg)
            }
        }.trim()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                PsychRu.paywallTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = Forest
            )
            SpeakIconButton(text = speakText, tint = Forest)
        }
        Text(PsychRu.paywallBody, style = MaterialTheme.typography.bodyLarge, color = Forest)
        Text(PsychRu.upsell, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        priceRub?.let { price ->
            Text(
                PsychRu.paywallPrice.format(price),
                style = MaterialTheme.typography.titleMedium,
                color = Amber
            )
        }
        Text(PsychRu.paywallHint, color = Moss, style = MaterialTheme.typography.bodySmall)
        pay.message?.let { msg ->
            Text(msg, color = Amber, style = MaterialTheme.typography.bodyMedium)
        }
        if (paymentsEnabled || pay.paymentsEnabled) {
            PrimaryBtn(
                PsychRu.paywallSupport,
                enabled = !pay.busy
            ) {
                vm.startPremiumPayment { url ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            }
        } else {
            Text(
                "Онлайн-оплата подключается на сервере. Сумма уже видна — она специально небольшая.",
                color = Amber
            )
        }
        if (BuildConfig.DEBUG) {
            PrimaryBtn(PsychRu.grantPro) { vm.grantDebugPro() }
        }
        MenuBtn(PsychRu.mainMenu) { vm.goHub() }
    }
}

@Composable
private fun SessionListBody(
    postponed: Boolean,
    sessions: List<ru.na.step4.obidy.data.psych.PsychSession>,
    viewModel: PsychViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                if (postponed) PsychRu.postponed else PsychRu.completed,
                style = MaterialTheme.typography.titleLarge,
                color = Forest
            )
        }
        items(sessions, key = { it.id }) { session ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .clickable { viewModel.openSession(session.id) }
                    .padding(14.dp)
            ) {
                Text(
                    PsychLogic.formatLocal(session.createdAt, viewModel.settings.utcOffsetMinutes),
                    color = Forest
                )
            }
        }
    }
}

@Composable
private fun IdleBody(page: PsychPage.Idle, vm: PsychViewModel) {
    val snippet = PsychLogic.shortStory(
        page.situation.summary.ifBlank { page.situation.text },
        90
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(PsychRu.idleTitle, style = MaterialTheme.typography.titleLarge, color = Forest)
        if (snippet.isNotBlank()) {
            Text(
                "«$snippet»",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .padding(12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Forest
            )
        }
        Text(PsychRu.idleBody, color = Moss)
        PrimaryBtn(PsychRu.continueWork) { vm.continueIdle() }
        MenuBtn(PsychRu.finish) { vm.finishNow() }
        MenuBtn(PsychRu.postpone) { vm.postpone() }
        MenuBtn(PsychRu.startNew) { vm.goRecord() }
    }
}

@Composable
private fun ReviewBody(page: PsychPage.Review, vm: PsychViewModel, onOpenProfile: () -> Unit) {
    var moreOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpandedRecordBlock(page.situation.text)
        page.answers.forEach { QaCard(it) }
        if (page.session.analyzeText.isNotBlank()) {
            Text(PsychRu.analyze, style = MaterialTheme.typography.titleMedium, color = Forest)
            ReadableText(page.session.analyzeText)
        }
        if (page.session.recommendText.isNotBlank()) {
            Text(PsychRu.recommend, style = MaterialTheme.typography.titleMedium, color = Forest)
            ReadableText(page.session.recommendText)
        }
        if (page.session.assistantText.isNotBlank()) {
            Text(PsychRu.assistant, style = MaterialTheme.typography.titleMedium, color = Forest)
            ReadableText(page.session.assistantText)
        }
        if (!moreOpen) {
            MenuBtn(PsychRu.furtherActions) { moreOpen = true }
        } else {
            MenuBtn(PsychRu.analyze) { vm.analyze() }
            MenuBtn(PsychRu.recommend) { vm.recommend() }
            MenuBtn(PsychRu.work) { vm.startWork() }
            MenuBtn(PsychRu.postpone) { vm.postpone() }
            MenuBtn(PsychRu.finishSituation) { vm.finishNow() }
            if (vm.settings.hasEmptyProfileField()) {
                MenuBtn(PsychRu.fillProfile) { onOpenProfile() }
            }
        }
    }
}

@Composable
private fun ExpandedRecordBlock(text: String) {
    if (text.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                PsychRu.yourRecord,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = Amber
            )
            SpeakIconButton(text = text, tint = Forest)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Forest
        )
    }
}

@Composable
private fun CollapsedRecordBlock(text: String, forceCollapsed: Boolean = false) {
    if (text.isBlank()) return
    var open by remember(text) { mutableStateOf(false) }
    LaunchedEffect(forceCollapsed) {
        if (forceCollapsed) open = false
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SandDeep.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                PsychRu.yourRecord,
                modifier = Modifier
                    .weight(1f)
                    .clickable { open = !open }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Amber
            )
            SpeakIconButton(text = text, tint = Forest)
            Icon(
                imageVector = if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = Forest,
                modifier = Modifier
                    .clickable { open = !open }
                    .padding(8.dp)
            )
        }
        if (open) {
            Text(
                text,
                modifier = Modifier
                    .clickable { open = false }
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Forest
            )
        }
    }
}

@Composable
private fun QuestionBlock(
    question: String,
    waiting: Boolean,
    waitingTitle: String = PsychRu.waitingQuestion
) {
    if (waiting) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SandDeep.copy(alpha = 0.72f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Forest,
                strokeWidth = 3.dp
            )
            Text(
                waitingTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Forest,
                textAlign = TextAlign.Center
            )
            Text(
                PsychRu.voiceHint,
                style = MaterialTheme.typography.bodySmall,
                color = Moss,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    if (question.isBlank()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            PsychRu.psychologistName,
            style = MaterialTheme.typography.labelLarge,
            color = Forest
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                question,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = Forest
            )
            SpeakIconButton(text = question, tint = Forest)
        }
    }
}

@Composable
private fun ReadableText(text: String) {
    SpeakableText(text) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SandDeep.copy(alpha = 0.72f))
                .padding(14.dp)
        ) {
            text.lines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.startsWith("**") && line.endsWith("**") -> {
                        Spacer(Modifier.height(8.dp))
                        Text(line.trim('*'), style = MaterialTheme.typography.titleMedium, color = Forest)
                    }
                    line.isBlank() -> Spacer(Modifier.height(6.dp))
                    else -> Text(
                        line.replace("**", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QaCard(qa: PsychQa) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                qa.question,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Forest
            )
            SpeakIconButton(text = qa.question, tint = Forest)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                qa.answer,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SpeakIconButton(text = qa.answer, tint = Forest)
        }
    }
}

private fun viewItemSpeakText(item: ViewItem): String = buildString {
    append(item.situation.text.trim())
    item.answers.forEach { qa ->
        val question = qa.question.trim()
        val answer = qa.answer.trim()
        if (question.isNotEmpty()) {
            append("\n\n")
            append(question)
        }
        if (answer.isNotEmpty()) {
            append('\n')
            append(answer)
        }
    }
}.trim()

@Composable
private fun rememberScrollToEndOnGrow(text: String): androidx.compose.foundation.ScrollState {
    val scroll = rememberScrollState()
    var prevLen by remember { mutableIntStateOf(text.length) }
    LaunchedEffect(text) {
        val grew = text.length > prevLen
        prevLen = text.length
        if (!grew) return@LaunchedEffect
        delay(48)
        scroll.animateScrollTo(scroll.maxValue)
        delay(96)
        scroll.animateScrollTo(scroll.maxValue)
    }
    return scroll
}

@Composable
private fun PsychWriteColumn(
    text: String,
    onChange: (String) -> Unit,
    hint: String,
    composing: Boolean,
    showField: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollToEndOnGrow(text)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            header()
            if (showField) {
                PsychField(
                    text,
                    onChange,
                    hint,
                    minLines = if (composing) 8 else 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        footer()
    }
}

@Composable
private fun PsychField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    minLines: Int = 2,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    VoiceOutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.heightIn(min = 88.dp),
        minLines = minLines,
        placeholder = { Text(hint) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Forest,
            unfocusedBorderColor = Moss.copy(alpha = 0.35f),
            focusedContainerColor = Sand.copy(alpha = 0.7f),
            unfocusedContainerColor = Sand.copy(alpha = 0.45f),
            cursorColor = Forest
        )
    )
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
private fun PrimaryBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (label == PsychRu.send) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
            Spacer(Modifier.size(6.dp))
        }
        Text(label)
    }
}

@Composable
private fun MenuBtn(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) { Text(label, color = Forest) }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = Forest)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Forest)
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Forest,
    selectedLabelColor = Sand,
    containerColor = SandDeep,
    labelColor = Forest
)

private fun sharePlain(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, Ru.sectionPsych)
    }
    context.startActivity(Intent.createChooser(intent, PsychRu.share))
}
