package ru.na.step4.obidy.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.MainActivity
import ru.na.step4.obidy.data.psych.PsychReminderWorker
import ru.na.step4.obidy.data.InventoryProgressStore
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.TwelveSteps
import ru.na.step4.obidy.data.analysis.AnalysisAnswers
import ru.na.step4.obidy.data.analysis.AnalysisCatalog
import ru.na.step4.obidy.data.i18n.ScreenBundle
import ru.na.step4.obidy.data.journal.JournalFieldKind
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.life.LifeBoardRu
import ru.na.step4.obidy.data.life.LifeKind
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.data.profile.ProfileRu
import ru.na.step4.obidy.ui.analysis.AnalysisDetailScreen
import ru.na.step4.obidy.ui.analysis.AnalysisEditScreen
import ru.na.step4.obidy.ui.analysis.AnalysisEditorViewModel
import ru.na.step4.obidy.ui.analysis.AnalysisHistoryScreen
import ru.na.step4.obidy.ui.analysis.AnalysisHistoryViewModel
import ru.na.step4.obidy.ui.analysis.AnalysisHubScreen
import ru.na.step4.obidy.ui.analysis.AnalysisSessionScreen
import ru.na.step4.obidy.ui.analysis.AnalysisSessionViewModel
import ru.na.step4.obidy.ui.analysis.AnalysisSettingsScreen
import ru.na.step4.obidy.ui.components.LocalNotesRepository
import ru.na.step4.obidy.ui.i18n.EnsureTranslations
import ru.na.step4.obidy.ui.journal.JournalAiMode
import ru.na.step4.obidy.ui.journal.JournalAiScreen
import ru.na.step4.obidy.ui.journal.JournalEntriesScreen
import ru.na.step4.obidy.ui.journal.JournalEntryScreen
import ru.na.step4.obidy.ui.journal.JournalHubScreen
import ru.na.step4.obidy.ui.journal.JournalOnboardingScreen
import ru.na.step4.obidy.ui.journal.JournalPickScreen
import ru.na.step4.obidy.ui.journal.JournalSelectedScreen
import ru.na.step4.obidy.ui.journal.JournalSettingsScreen
import ru.na.step4.obidy.ui.journal.JournalSimpleScreen
import ru.na.step4.obidy.ui.journal.VersionScreen
import ru.na.step4.obidy.ui.journal.JournalViewModel
import ru.na.step4.obidy.ui.journal.JournalWordPickerScreen
import ru.na.step4.obidy.ui.life.LifeBoardScreen
import ru.na.step4.obidy.ui.life.LifeBoardViewModel
import ru.na.step4.obidy.ui.messenger.MessengerHostScreen
import ru.na.step4.obidy.ui.messenger.MessengerViewModel
import ru.na.step4.obidy.ui.profile.ProfileScreen
import ru.na.step4.obidy.ui.profile.ProfileViewModel
import ru.na.step4.obidy.ui.psych.PsychHostScreen
import ru.na.step4.obidy.ui.psych.PsychViewModel
import ru.na.step4.obidy.voicehands.VoiceHandsHost
import ru.na.step4.obidy.voicehands.VoiceHandsPsychGate
import ru.na.step4.obidy.ui.spiritual.SpiritualStatsScreen
import ru.na.step4.obidy.ui.support.FeedbackHost
import androidx.navigation.NavBackStackEntry
import androidx.compose.runtime.collectAsState

private object Routes {
    const val HOME = "home"
    const val STEPS = "steps"
    const val STEP4 = "step4"
    const val STEP = "step/{n}"
    const val SOON = "soon/{kind}"
    const val LIST = "list"
    const val GUIDE = "guide"
    const val CATEGORIES = "categories"
    const val ASSISTANT = "assistant"
    const val ASSISTANT_FOCUS = "assistant/{situationId}/{focus}"
    const val EDIT = "edit/{id}"
    const val SITUATION = "situation/{id}"
    const val ANALYSIS = "analysis"
    const val ANALYSIS_SETTINGS = "analysis/settings"
    const val ANALYSIS_EDIT = "analysis/edit/{id}"
    const val ANALYSIS_HISTORY = "analysis/history"
    const val ANALYSIS_SESSION = "analysis/session/{id}"
    const val ANALYSIS_DETAIL = "analysis/detail/{id}"
    const val PSYCH = "psych"
    const val PROFILE = "profile"
    const val SPIRITUAL_STATS = "spiritual/stats"
    const val JOURNAL = "journal"
    const val JOURNAL_PICK = "journal/pick"
    const val JOURNAL_SELECTED = "journal/selected"
    const val JOURNAL_ENTRIES = "journal/entries"
    const val JOURNAL_ENTRY = "journal/entry/{id}"
    const val JOURNAL_PERSONALITY = "journal/personality"
    const val JOURNAL_AI_HELP = "journal/ai/help"
    const val JOURNAL_AI_HELP_ENTRY = "journal/ai/help/{id}"
    const val JOURNAL_AI_ANALYZE = "journal/ai/analyze"
    const val JOURNAL_AI_ANALYZE_ENTRY = "journal/ai/analyze/{id}"
    const val JOURNAL_SETTINGS = "journal/settings"
    const val JOURNAL_HELP = "journal/help"
    const val JOURNAL_SUPPORT = "journal/support"
    const val JOURNAL_VERSION = "journal/version"
    const val JOURNAL_PRO = "journal/pro"
    const val JOURNAL_WORDS = "journal/words/{fieldId}/{kind}"
    const val LIFE = "life/{kind}"
    const val MESSENGER = "messenger"

    fun step(n: Int) = "step/$n"
    fun soon(kind: String) = "soon/$kind"
    fun edit(id: Long) = "edit/$id"
    fun situation(id: Long) = "situation/$id"
    fun assistantFocus(situationId: Long, focus: String) = "assistant/$situationId/$focus"
    fun analysisSession(id: String) = "analysis/session/$id"
    fun analysisDetail(id: Long) = "analysis/detail/$id"
    fun analysisEdit(id: String) = "analysis/edit/$id"
    fun journalEntry(id: String) = "journal/entry/$id"
    fun journalAnalyzeEntry(id: String) = "journal/ai/analyze/$id"
    fun journalHelpEntry(id: String) = "journal/ai/help/$id"
    fun journalWords(fieldId: String, kind: String) = "journal/words/$fieldId/$kind"
    fun life(kind: String) = "life/$kind"
}

private suspend fun NavHostController.openInventoryAt(
    repository: ResentmentRepository,
    progress: InventoryProgressStore
) {
    fun goList() {
        navigate(Routes.LIST) { launchSingleTop = true }
    }
    val situationId = progress.lastSituationId
    if (situationId > 0L) {
        repository.getSituation(situationId)?.let { situation ->
            goList()
            navigate(Routes.edit(situation.resentmentId))
            navigate(Routes.situation(situation.id))
            return
        }
    }
    val resentmentId = progress.lastResentmentId
    if (resentmentId > 0L) {
        repository.getById(resentmentId)?.let {
            goList()
            navigate(Routes.edit(it.id))
            return
        }
    }
    goList()
}

private fun goToResentmentList(navController: NavHostController) {
    if (!navController.popBackStack(Routes.LIST, inclusive = false)) {
        navController.navigate(Routes.LIST)
    }
}

private fun openInventoryFromJournal(
    navController: NavHostController,
    app: Step4App,
    repository: ResentmentRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    selectPlace: () -> Unit
) {
    selectPlace()
    scope.launch {
        navController.openInventoryAt(repository, app.inventoryProgress)
    }
}

@Composable
fun Step4Nav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as Step4App
    val repository = app.repository
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalNotesRepository provides app.notesRepository) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.notesRepository.sync()
            ru.na.step4.obidy.data.analysis.AnalysisCatalogSync.sync(
                app.analysisSettings,
                app.journalPrefs
            )
            app.messengerRepository.refreshEnabled()
        }
    }
    val revision by app.i18nController.revision.collectAsStateWithLifecycle()
    val messengerOn by app.messengerRepository.enabled.collectAsStateWithLifecycle()
    val messengerInvite by app.messengerRepository.pendingInvite.collectAsStateWithLifecycle()
    LaunchedEffect(messengerInvite, messengerOn) {
        if (!messengerInvite.isNullOrBlank() && messengerOn &&
            navController.currentDestination?.route != Routes.MESSENGER
        ) {
            navController.navigate(Routes.MESSENGER)
        }
    }
    val psychOpenTick = (context as? MainActivity)?.psychOpenTick ?: 0
    LaunchedEffect(psychOpenTick) {
        if (psychOpenTick == 0) return@LaunchedEffect
        val activity = context as? MainActivity ?: return@LaunchedEffect
        if (!activity.pendingPsychOpen) return@LaunchedEffect
        val intent = activity.intent
        if (intent.getBooleanExtra(PsychReminderWorker.EXTRA_OPEN_PSYCH, false)) {
            intent.removeExtra(PsychReminderWorker.EXTRA_OPEN_PSYCH)
        }
        if (navController.currentDestination?.route != Routes.PSYCH) {
            navController.openMain(Routes.PSYCH)
        }
    }
    val resumeId = remember { app.analysisProgress.lastActiveId() }
    val startDestination = if (resumeId.isNullOrBlank()) {
        Routes.HOME
    } else {
        Routes.analysisSession(resumeId)
    }
    val currentRoute by navController.currentBackStackEntryAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val menuItems = remember(revision, messengerOn) {
        buildList {
            add(AppMenuItem(Routes.HOME, Ru.menuHome, Icons.Outlined.Home))
            add(AppMenuItem(Routes.PROFILE, ProfileRu.title, Icons.Outlined.Person))
            if (messengerOn) {
                add(AppMenuItem(Routes.MESSENGER, MessengerRu.title, Icons.Outlined.Forum))
            }
            add(AppMenuItem(Routes.life(LifeKind.GOAL), LifeBoardRu.goals, Icons.Outlined.Flag))
            add(AppMenuItem(Routes.life(LifeKind.IDEA), LifeBoardRu.ideas, Icons.Outlined.Lightbulb))
            add(AppMenuItem(Routes.life(LifeKind.EVENT), LifeBoardRu.calendar, Icons.Outlined.CalendarMonth))
            add(AppMenuItem(Routes.life(LifeKind.NOTE), LifeBoardRu.notes, Icons.Outlined.Notes))
            add(AppMenuItem(Routes.JOURNAL, Ru.sectionSteps, Icons.Outlined.AutoStories))
            add(AppMenuItem(Routes.ANALYSIS, Ru.sectionAnalysis, Icons.Outlined.SelfImprovement))
            add(AppMenuItem(Routes.PSYCH, Ru.sectionPsych, Icons.Outlined.Psychology))
            add(AppMenuItem(Routes.JOURNAL_SETTINGS, JournalRu.settings, Icons.Outlined.Settings))
        }
    }
    AppMenuDrawer(
        drawerState = drawerState,
        selectedRoute = drawerSelection(currentRoute?.filledRoute()),
        items = menuItems,
        onOpenRoute = { navController.openMain(it) }
    ) {
    Box(Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) {
            val dd by app.spiritualRating.snapshot.collectAsState()
            EnsureTranslations(ScreenBundle.HOME) {
                HomeScreen(
                    ddTotal = dd.totalScore,
                    onDdStats = { navController.navigate(Routes.SPIRITUAL_STATS) },
                    onRefreshDd = { app.spiritualRating.refreshMissPenalties() },
                    onSteps = { navController.navigate(Routes.JOURNAL) },
                    onAnalysis = { navController.navigate(Routes.ANALYSIS) },
                    onPsych = { navController.navigate(Routes.PSYCH) },
                    onGoals = { navController.navigate(Routes.life(LifeKind.GOAL)) },
                    onIdeas = { navController.navigate(Routes.life(LifeKind.IDEA)) },
                    onCalendar = { navController.navigate(Routes.life(LifeKind.EVENT)) },
                    onNotes = { navController.navigate(Routes.life(LifeKind.NOTE)) },
                    onProfile = { navController.navigate(Routes.PROFILE) },
                    onSettings = { navController.navigate(Routes.JOURNAL_SETTINGS) },
                    showMessenger = messengerOn,
                    onMessenger = { navController.navigate(Routes.MESSENGER) }
                )
            }
        }
        composable(Routes.MESSENGER) {
            val vm: MessengerViewModel = viewModel(
                factory = MessengerViewModel.factory(app.messengerRepository)
            )
            EnsureTranslations(ScreenBundle.MESSENGER) {
                MessengerHostScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Routes.LIFE,
            arguments = listOf(navArgument("kind") { type = NavType.StringType })
        ) { entry ->
            val kind = LifeKind.normalize(entry.arguments?.getString("kind"))
            val vm: LifeBoardViewModel = viewModel(
                key = "life-$kind",
                factory = LifeBoardViewModel.factory(app.lifeBoard, kind, app.supportRepository)
            )
            EnsureTranslations(ScreenBundle.LIFE) {
                LifeBoardScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.SPIRITUAL_STATS) {
            EnsureTranslations(ScreenBundle.SPIRITUAL) {
                SpiritualStatsScreen(
                    store = app.spiritualRating,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.PROFILE) {
            val vm: ProfileViewModel = viewModel(
                factory = ProfileViewModel.factory(
                    store = app.profileStore,
                    canCollect = {
                        app.psychSettings.isPro || app.journalPrefs.isPro || app.journalPrefs.isAdmin
                    },
                    onLanguageChanged = { code ->
                        app.psychSettings.languageCode = code
                        app.i18nController.setLanguage(code)
                        ru.na.steps12.voice.VoiceI18n.speechTag =
                            ru.na.step4.obidy.data.i18n.LocaleHelper.speechTag(code)
                    }
                )
            )
            EnsureTranslations(ScreenBundle.PROFILE) {
                ProfileScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.ANALYSIS) {
            EnsureTranslations(ScreenBundle.ANALYSIS) {
                AnalysisHubScreen(
                    settings = app.analysisSettings,
                    progress = app.analysisProgress,
                    streak = app.analysisStreak,
                    onBack = { navController.popBackStack() },
                    onOpen = { menuId ->
                        val id = AnalysisCatalog.resolveSessionId(menuId, app.analysisSettings)
                        navController.navigate(Routes.analysisSession(id))
                    },
                    onHistory = { navController.navigate(Routes.ANALYSIS_HISTORY) },
                    onSettings = { navController.navigate(Routes.ANALYSIS_SETTINGS) },
                    onProfile = { navController.navigate(Routes.PROFILE) }
                )
            }
        }
        composable(Routes.ANALYSIS_SETTINGS) {
            AnalysisSettingsScreen(
                settings = app.analysisSettings,
                prefs = app.journalPrefs,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.analysisEdit(it)) }
            )
        }
        composable(
            route = Routes.ANALYSIS_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: AnalysisEditorViewModel = viewModel(
                key = "analysis-edit-$id",
                factory = AnalysisEditorViewModel.factory(
                    app,
                    app.analysisSettings,
                    app.journalPrefs,
                    id
                )
            )
            AnalysisEditScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ANALYSIS_HISTORY) {
            val vm: AnalysisHistoryViewModel = viewModel(
                factory = AnalysisHistoryViewModel.factory(app.analysisRepository)
            )
            AnalysisHistoryScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.analysisDetail(id)) }
            )
        }
        composable(
            route = Routes.ANALYSIS_SESSION,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: AnalysisSessionViewModel = viewModel(
                key = "analysis-$id",
                factory = AnalysisSessionViewModel.factory(
                    app = app,
                    repository = app.analysisRepository,
                    settings = app.analysisSettings,
                    catalogId = id
                )
            )
            AnalysisSessionScreen(
                viewModel = vm,
                onLeave = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            launchSingleTop = true
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                        navController.navigate(Routes.ANALYSIS)
                    }
                }
            )
        }
        composable(
            route = Routes.ANALYSIS_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val record by app.analysisRepository.observeById(id)
                .collectAsStateWithLifecycle(initialValue = null)
            AnalysisDetailScreen(
                record = record,
                onBack = { navController.popBackStack() },
                onDelete = {
                    scope.launch { app.analysisRepository.delete(id) }
                    navController.popBackStack()
                },
                onAppendAnswers = { extra ->
                    scope.launch {
                        val rec = app.analysisRepository.getById(id) ?: return@launch
                        val current = AnalysisAnswers.decode(rec.answersJson)
                        app.analysisRepository.replaceAnswers(id, current + extra)
                    }
                }
            )
        }
        composable(Routes.PSYCH) {
            val vm: PsychViewModel = viewModel(
                factory = PsychViewModel.factory(
                    app.psychRepository,
                    app.psychSettings,
                    app.spiritualRating,
                    app.journalPrefs
                )
            )
            DisposableEffect(vm) {
                VoiceHandsPsychGate.attach(vm)
                onDispose { VoiceHandsPsychGate.detach(vm) }
            }
            EnsureTranslations(ScreenBundle.PSYCH) {
                PsychHostScreen(
                    viewModel = vm,
                    onLeaveAppHome = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) }
                )
            }
        }
        composable(Routes.JOURNAL) {
            val vm = journalVm(it, navController, app)
            val registered = vm.state.collectAsStateWithLifecycle().value.registered
            EnsureTranslations(ScreenBundle.JOURNAL) {
                if (!registered) {
                    JournalOnboardingScreen(vm)
                } else {
                    JournalHubScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onPick = { navController.navigate(Routes.JOURNAL_PICK) },
                        onEntries = { navController.navigate(Routes.JOURNAL_ENTRIES) },
                        onPersonality = { navController.navigate(Routes.PROFILE) },
                        onAiHelp = { navController.navigate(Routes.JOURNAL_AI_HELP) },
                        onAiAnalyze = { id ->
                            navController.navigate(Routes.journalAnalyzeEntry(id))
                        },
                        onSettings = { navController.navigate(Routes.JOURNAL_SETTINGS) },
                        onHelp = { navController.navigate(Routes.JOURNAL_HELP) },
                        onSupport = { navController.navigate(Routes.JOURNAL_SUPPORT) },
                        onResentments = {
                            openInventoryFromJournal(navController, app, repository, scope) {
                                vm.selectResentmentPlace()
                            }
                        }
                    )
                }
            }
        }
        composable(Routes.JOURNAL_PICK) {
            val vm = journalVm(it, navController, app)
            EnsureTranslations(ScreenBundle.JOURNAL_TREE) {
                JournalPickScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSelected = { navController.navigate(Routes.JOURNAL_SELECTED) },
                    onResentments = {
                        openInventoryFromJournal(navController, app, repository, scope) {
                            vm.selectResentmentPlace()
                        }
                    },
                    onEditEntry = {
                        navController.popBackStack(Routes.JOURNAL, inclusive = false)
                    },
                    onAiAnalyze = { entryId, forceNew ->
                        vm.prepareAnalyzeNavigation(forceNew)
                        navController.navigate(Routes.journalAnalyzeEntry(entryId))
                    }
                )
            }
        }
        composable(Routes.JOURNAL_SELECTED) {
            val vm = journalVm(it, navController, app)
            JournalSelectedScreen(
                viewModel = vm,
                onMenu = {
                    navController.popBackStack(Routes.JOURNAL, inclusive = false)
                },
                onPickParent = {
                    navController.popBackStack(Routes.JOURNAL_PICK, inclusive = false)
                },
                onAiHelp = { navController.navigate(Routes.JOURNAL_AI_HELP) },
                onAiAnalyze = { id ->
                    navController.navigate(Routes.journalAnalyzeEntry(id))
                },
                onResentments = {
                    openInventoryFromJournal(navController, app, repository, scope) {
                        vm.selectResentmentPlace()
                    }
                },
                onEntries = { navController.navigate(Routes.JOURNAL_ENTRIES) }
            )
        }
        composable(Routes.JOURNAL_ENTRIES) {
            val vm = journalVm(it, navController, app)
            JournalEntriesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.journalEntry(id)) },
                onAiAnalyze = { id ->
                    if (id == null) navController.navigate(Routes.JOURNAL_AI_ANALYZE)
                    else navController.navigate(Routes.journalAnalyzeEntry(id))
                }
            )
        }
        composable(
            route = Routes.JOURNAL_ENTRY,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm = journalVm(entry, navController, app)
            JournalEntryScreen(
                viewModel = vm,
                entryId = id,
                onBack = { navController.popBackStack() },
                onEdit = {
                    vm.state.value.entries.find { it.id == id }?.let(vm::startEdit)
                    navController.popBackStack(Routes.JOURNAL, inclusive = false)
                },
                onAiAnalyze = { navController.navigate(Routes.journalAnalyzeEntry(it)) },
                onAiHelp = { navController.navigate(Routes.journalHelpEntry(it)) }
            )
        }
        composable(Routes.JOURNAL_PERSONALITY) {
            val vm: ProfileViewModel = viewModel(
                factory = ProfileViewModel.factory(
                    store = app.profileStore,
                    canCollect = {
                        app.psychSettings.isPro || app.journalPrefs.isPro || app.journalPrefs.isAdmin
                    },
                    onLanguageChanged = { code ->
                        app.psychSettings.languageCode = code
                        app.i18nController.setLanguage(code)
                        ru.na.steps12.voice.VoiceI18n.speechTag =
                            ru.na.step4.obidy.data.i18n.LocaleHelper.speechTag(code)
                    }
                )
            )
            ProfileScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.JOURNAL_AI_HELP) {
            JournalAiScreen(
                viewModel = journalVm(it, navController, app),
                mode = JournalAiMode.HELP,
                entryId = null,
                onBack = { navController.popBackStack(Routes.JOURNAL, inclusive = false) },
                onPro = { navController.navigate(Routes.JOURNAL_PRO) }
            )
        }
        composable(
            route = Routes.JOURNAL_AI_HELP_ENTRY,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id")
            JournalAiScreen(
                viewModel = journalVm(entry, navController, app),
                mode = JournalAiMode.HELP,
                entryId = id,
                onBack = { navController.popBackStack(Routes.JOURNAL, inclusive = false) },
                onPro = { navController.navigate(Routes.JOURNAL_PRO) }
            )
        }
        composable(Routes.JOURNAL_AI_ANALYZE) {
            JournalAiScreen(
                viewModel = journalVm(it, navController, app),
                mode = JournalAiMode.ANALYZE,
                entryId = null,
                onBack = { navController.popBackStack(Routes.JOURNAL, inclusive = false) },
                onPro = { navController.navigate(Routes.JOURNAL_PRO) }
            )
        }
        composable(
            route = Routes.JOURNAL_AI_ANALYZE_ENTRY,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id")
            JournalAiScreen(
                viewModel = journalVm(entry, navController, app),
                mode = JournalAiMode.ANALYZE,
                entryId = id,
                onBack = { navController.popBackStack(Routes.JOURNAL, inclusive = false) },
                onPro = { navController.navigate(Routes.JOURNAL_PRO) }
            )
        }
        composable(
            route = Routes.JOURNAL_WORDS,
            arguments = listOf(
                navArgument("fieldId") { type = NavType.StringType },
                navArgument("kind") { type = NavType.StringType }
            )
        ) { entry ->
            val fieldId = entry.arguments?.getString("fieldId").orEmpty()
            val kind = runCatching {
                JournalFieldKind.valueOf(entry.arguments?.getString("kind").orEmpty())
            }.getOrDefault(JournalFieldKind.FEELINGS)
            JournalWordPickerScreen(
                viewModel = journalVm(entry, navController, app),
                fieldId = fieldId,
                kind = kind,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.JOURNAL_SETTINGS) {
            JournalSettingsScreen(
                viewModel = journalVm(it, navController, app),
                analysisSettings = app.analysisSettings,
                journalPrefs = app.journalPrefs,
                onBack = { navController.popBackStack() },
                onEditAnalysis = { navController.navigate(Routes.analysisEdit(it)) },
                onVersion = { navController.navigate(Routes.JOURNAL_VERSION) }
            )
        }
        composable(Routes.JOURNAL_VERSION) {
            VersionScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.JOURNAL_HELP) {
            JournalSimpleScreen(
                title = JournalRu.help,
                body = JournalRu.helpBody,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.JOURNAL_SUPPORT) {
            JournalSimpleScreen(
                title = JournalRu.support,
                body = JournalRu.supportBody,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.JOURNAL_PRO) {
            JournalSimpleScreen(
                title = JournalRu.proNeededTitle,
                body = JournalRu.proDetails,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.STEPS) {
            StepsScreen(
                onBack = { navController.popBackStack() },
                onStep = { step ->
                    if (step.number == 4) {
                        navController.navigate(Routes.STEP4)
                    } else {
                        navController.navigate(Routes.step(step.number))
                    }
                }
            )
        }
        composable(Routes.STEP4) {
            Step4HubScreen(
                onBack = { navController.popBackStack() },
                onResentments = {
                    scope.launch {
                        navController.openInventoryAt(repository, app.inventoryProgress)
                    }
                },
                onComingSoon = { title ->
                    val kind = when (title) {
                        Ru.inventoryFears -> "fears"
                        Ru.inventorySex -> "sex"
                        Ru.inventoryHarms -> "harms"
                        else -> "other"
                    }
                    navController.navigate(Routes.soon(kind))
                }
            )
        }
        composable(
            route = Routes.STEP,
            arguments = listOf(navArgument("n") { type = NavType.IntType })
        ) { entry ->
            val n = entry.arguments?.getInt("n") ?: 1
            val step = TwelveSteps.byNumber(n)
            PlaceholderScreen(
                title = step?.let { "${it.number}. ${it.shortTitle}" } ?: Ru.stepsTitle,
                body = step?.text ?: Ru.placeholderSoon,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SOON,
            arguments = listOf(navArgument("kind") { type = NavType.StringType })
        ) { entry ->
            val kind = entry.arguments?.getString("kind").orEmpty()
            val title = when (kind) {
                "analysis" -> Ru.sectionAnalysis
                "psych" -> Ru.sectionPsych
                "fears" -> Ru.inventoryFears
                "sex" -> Ru.inventorySex
                "harms" -> Ru.inventoryHarms
                else -> Ru.comingSoon
            }
            val body = when (kind) {
                "analysis" -> Ru.sectionAnalysisBody
                "psych" -> Ru.sectionPsychBody
                else -> Ru.placeholderSoon
            }
            PlaceholderScreen(
                title = title,
                body = body,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.LIST) {
            val vm: ListViewModel = viewModel(factory = ListViewModel.factory(repository))
            EnsureTranslations(ScreenBundle.INVENTORY) {
                ListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.edit(id)) },
                    onGuide = { navController.navigate(Routes.GUIDE) },
                    onCategories = { navController.navigate(Routes.CATEGORIES) },
                    onAssistant = { navController.navigate(Routes.ASSISTANT) }
                )
            }
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
            DisposableEffect(activity) {
                vm.attachHost(activity, activity.lifecycle)
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
            route = Routes.ASSISTANT_FOCUS,
            arguments = listOf(
                navArgument("situationId") { type = NavType.LongType },
                navArgument("focus") { type = NavType.StringType }
            )
        ) { entry ->
            val situationId = entry.arguments?.getLong("situationId") ?: -1L
            val focus = entry.arguments?.getString("focus").orEmpty()
            val vm: AssistantViewModel = viewModel(
                key = "assist-$situationId-$focus",
                factory = AssistantViewModel.factory(
                    app = app as Application,
                    repository = repository,
                    situationId = situationId,
                    focusKey = focus
                )
            )
            DisposableEffect(activity) {
                vm.attachHost(activity, activity.lifecycle)
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
            LaunchedEffect(id) {
                if (id > 0L) app.inventoryProgress.markResentment(id)
            }
            val vm: EditViewModel = viewModel(
                factory = EditViewModel.factory(repository, id)
            )
            EditScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenList = { goToResentmentList(navController) },
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
            LaunchedEffect(id) {
                if (id > 0L) {
                    repository.getSituation(id)?.let { situation ->
                        app.inventoryProgress.markSituation(situation.id, situation.resentmentId)
                    }
                }
            }
            val vm: SituationEditViewModel = viewModel(
                factory = SituationEditViewModel.factory(
                    repository, id, app.journalPrefs, app.lifeBoard, app.inventoryAiCache
                )
            )
            SituationEditScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenList = { goToResentmentList(navController) },
                onAssistantFocus = { situationId, focus ->
                    navController.navigate(Routes.assistantFocus(situationId, focus))
                },
                onPro = { navController.navigate(Routes.JOURNAL_PRO) }
            )
        }
    }
    FeedbackHost(
        repository = app.supportRepository,
        route = currentRoute?.filledRoute(),
        modifier = Modifier.fillMaxSize()
    )
    VoiceHandsHost(
        onOpenPsych = {
            if (navController.currentDestination?.route != Routes.PSYCH) {
                navController.openMain(Routes.PSYCH)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    }
    }
    }
}

private fun NavBackStackEntry.filledRoute(): String {
    val pattern = destination.route.orEmpty()
    var filled = pattern
    arguments?.keySet()?.forEach { key ->
        val value = arguments?.get(key) ?: return@forEach
        filled = filled.replace("{$key}", value.toString())
    }
    return filled
}

private fun drawerSelection(route: String?): String {
    val r = route ?: return Routes.HOME
    return when {
        r == Routes.HOME -> Routes.HOME
        r == Routes.SPIRITUAL_STATS -> Routes.HOME
        r == Routes.PROFILE -> Routes.PROFILE
        r.startsWith("messenger") -> Routes.MESSENGER
        r.startsWith("life/") -> r
        r == Routes.JOURNAL_SETTINGS -> Routes.JOURNAL_SETTINGS
        r.startsWith("journal") -> Routes.JOURNAL
        r.startsWith("analysis") -> Routes.ANALYSIS
        r.startsWith("psych") -> Routes.PSYCH
        r.startsWith("step") ||
            r == Routes.LIST ||
            r.startsWith("edit") ||
            r.startsWith("situation") ||
            r.startsWith("assistant") ||
            r == Routes.GUIDE ||
            r == Routes.CATEGORIES ||
            r.startsWith("soon") -> Routes.JOURNAL
        else -> ""
    }
}

private fun NavHostController.openMain(route: String) {
    if (currentBackStackEntry?.filledRoute() == route) return
    if (route == Routes.HOME) {
        if (!popBackStack(Routes.HOME, inclusive = false)) {
            navigate(Routes.HOME) {
                launchSingleTop = true
                popUpTo(graph.id) { inclusive = true }
            }
        }
        return
    }
    val hasHome = runCatching { getBackStackEntry(Routes.HOME) }.isSuccess
    if (hasHome) {
        navigate(route) {
            launchSingleTop = true
            popUpTo(Routes.HOME) { inclusive = false }
        }
    } else {
        navigate(Routes.HOME) {
            launchSingleTop = true
            popUpTo(graph.id) { inclusive = true }
        }
        navigate(route) { launchSingleTop = true }
    }
}

@Composable
private fun journalVm(
    entry: NavBackStackEntry,
    nav: NavHostController,
    app: Step4App
): JournalViewModel {
    val parent = remember(entry) {
        runCatching { nav.getBackStackEntry(Routes.JOURNAL) }.getOrNull() ?: entry
    }
    return viewModel(
        viewModelStoreOwner = parent,
        factory = JournalViewModel.factory(app, app.journalStore, app.journalPrefs)
    )
}
