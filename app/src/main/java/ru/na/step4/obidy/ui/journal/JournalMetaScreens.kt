package ru.na.step4.obidy.ui.journal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.data.analysis.AnalysisSettings
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.journal.JournalProblems
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.ui.analysis.AnalysisCatalogTab
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.support.SupportInboxScreen
import ru.na.step4.obidy.data.support.SupportRu
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.LocalVoicePlugin
import ru.na.steps12.voice.ui.SpeakableText
import ru.na.steps12.voice.ui.VoiceOutlinedTextField
import ru.na.step4.obidy.voicehands.VoiceHandsSettingsPanel
import ru.na.steps12.voice.ui.VoiceSettingsPanel

@Composable
fun JournalOnboardingScreen(viewModel: JournalViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(if (state.name.isNotBlank()) 1 else 0) }
    var name by remember { mutableStateOf(state.name) }
    var problems by remember { mutableStateOf(state.problems) }

    Box(Modifier.fillMaxSize()) {
        AtmosphereBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppNavIcon()
            Text(JournalRu.onboardingHello, style = MaterialTheme.typography.headlineLarge, color = Forest)
            if (step == 0 && state.name.isBlank()) {
                Text(JournalRu.onboardingName, color = Forest)
                VoiceOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(JournalRu.onboardingNameHint) },
                    shape = RoundedCornerShape(12.dp)
                )
                JournalButton(JournalRu.onboardingNext, {
                    if (name.trim().isNotBlank()) step = 1
                }, filled = true)
            } else {
                Text(JournalRu.onboardingProblems, style = MaterialTheme.typography.titleLarge, color = Forest)
                Text(JournalRu.onboardingProblemsHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalProblems.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { option ->
                            FilterChip(
                                selected = option.key in problems,
                                onClick = {
                                    problems = problems.toMutableSet().also { set ->
                                        if (!set.add(option.key)) set.remove(option.key)
                                    }
                                },
                                label = { Text(option.label) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Forest,
                                    selectedLabelColor = Sand
                                )
                            )
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                JournalButton(JournalRu.done, {
                    viewModel.register(name.ifBlank { state.name }, problems)
                }, filled = true)
                JournalButton(JournalRu.skip, onClick = {
                    viewModel.register(name.ifBlank { state.name }, emptySet())
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSettingsScreen(
    viewModel: JournalViewModel,
    analysisSettings: AnalysisSettings,
    journalPrefs: JournalPrefs,
    onBack: () -> Unit,
    onEditAnalysis: (String) -> Unit,
    onVersion: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by remember(state.name) { mutableStateOf(state.name) }
    var rightsMenu by remember { mutableStateOf(false) }
    var askAdminCode by remember { mutableStateOf(false) }
    var adminCode by remember { mutableStateOf("") }
    var adminChecking by remember { mutableStateOf(false) }
    var adminError by remember { mutableStateOf<String?>(null) }
    var settingsTab by remember { mutableStateOf(0) }
    val openJson = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFromUri(context, uri)
    }
    val rightsLabel = when {
        state.isAdmin -> JournalRu.settingsAdmin
        state.isPro -> JournalRu.settingsPro
        else -> JournalRu.settingsFree
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(JournalRu.settings, color = Forest) },
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
                    .padding(top = 0.dp)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = settingsTab,
                    edgePadding = 12.dp,
                    containerColor = Sand.copy(alpha = 0.5f),
                    contentColor = Forest,
                    indicator = { positions ->
                        if (positions.isNotEmpty()) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(positions[settingsTab]),
                                color = Forest
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = settingsTab == 0,
                        onClick = { settingsTab = 0 },
                        text = { Text(JournalRu.settingsGeneral) }
                    )
                    Tab(
                        selected = settingsTab == 1,
                        onClick = { settingsTab = 1 },
                        text = { Text(JournalRu.settingsEntries) }
                    )
                    Tab(
                        selected = settingsTab == 2,
                        onClick = { settingsTab = 2 },
                        text = { Text(JournalRu.settingsAnalysis) }
                    )
                    if (state.isAdmin) {
                        Tab(
                            selected = settingsTab == 3,
                            onClick = { settingsTab = 3 },
                            text = { Text(SupportRu.inbox) }
                        )
                    }
                }
                if (settingsTab == 2) {
                    AnalysisCatalogTab(
                        settings = analysisSettings,
                        prefs = journalPrefs,
                        onEdit = onEditAnalysis
                    )
                } else if (settingsTab == 3 && state.isAdmin) {
                    SupportInboxScreen(
                        repository = (context.applicationContext as Step4App).supportRepository
                    )
                } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (settingsTab == 0) {
                Text(JournalRu.settingsName, color = Amber, style = MaterialTheme.typography.labelMedium)
                VoiceOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                JournalButton(Ru.save, onClick = { viewModel.setName(name) })
                Text(JournalRu.exportJson, color = Amber, style = MaterialTheme.typography.labelMedium)
                Text(
                    JournalRu.exportJsonHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                JournalButton(
                    JournalRu.exportJson,
                    onClick = { viewModel.exportToDownloads(context) }
                )
                JournalButton(
                    JournalRu.importJson,
                    onClick = {
                        openJson.launch(
                            arrayOf(
                                "application/json",
                                "text/json",
                                "text/plain",
                                "*/*"
                            )
                        )
                    }
                )
                Text(JournalRu.settingsPlace, color = Amber, style = MaterialTheme.typography.labelMedium)
                Text(state.path?.line() ?: JournalRu.noneSelected, color = Forest)
                Text(JournalRu.onboardingProblems, color = Amber, style = MaterialTheme.typography.labelMedium)
                JournalProblems.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { option ->
                            FilterChip(
                                selected = option.key in state.problems,
                                onClick = { viewModel.toggleProblem(option.key) },
                                label = { Text(option.label) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Forest,
                                    selectedLabelColor = Sand
                                )
                            )
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(JournalRu.settingsRights, color = Amber, style = MaterialTheme.typography.labelMedium)
                        Text(rightsLabel, color = Forest, style = MaterialTheme.typography.titleMedium)
                    }
                    Box {
                        IconButton(onClick = { rightsMenu = true }) {
                            Icon(
                                Icons.Outlined.ManageAccounts,
                                contentDescription = JournalRu.changeRights,
                                tint = Forest
                            )
                        }
                        DropdownMenu(expanded = rightsMenu, onDismissRequest = { rightsMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(JournalRu.settingsFree) },
                                onClick = {
                                    rightsMenu = false
                                    viewModel.setUserRole()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(JournalRu.settingsPro) },
                                onClick = {
                                    rightsMenu = false
                                    viewModel.setProRole()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(JournalRu.settingsAdmin) },
                                onClick = {
                                    rightsMenu = false
                                    if (state.isAdmin) return@DropdownMenuItem
                                    adminCode = ""
                                    adminError = null
                                    askAdminCode = true
                                }
                            )
                        }
                    }
                }
                if (!state.notice.isNullOrBlank()) {
                    Text(state.notice.orEmpty(), color = Amber)
                }
                val voicePlugin = LocalVoicePlugin.current
                if (voicePlugin != null) {
                    VoiceSettingsPanel(plugin = voicePlugin)
                }
                VoiceHandsSettingsPanel()
                Text(JournalRu.versionOpen, color = Amber, style = MaterialTheme.typography.labelMedium)
                JournalButton(
                    "${BuildConfig.APP_VERSION_NAME} · ${JournalRu.versionHistory}",
                    onClick = onVersion
                )
                    } else {
                        JournalFieldsSettings(viewModel)
                    }
                }
                }
            }
        }
    }
    if (askAdminCode) {
        AlertDialog(
            onDismissRequest = {
                if (!adminChecking) askAdminCode = false
            },
            title = { Text(JournalRu.settingsAdmin) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(JournalRu.adminCodePrompt, color = Forest)
                    OutlinedTextField(
                        value = adminCode,
                        onValueChange = {
                            adminCode = it
                            adminError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(JournalRu.adminCodeHint) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !adminChecking
                    )
                    if (!adminError.isNullOrBlank()) {
                        Text(adminError.orEmpty(), color = Amber)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !adminChecking && adminCode.isNotBlank(),
                    onClick = {
                        adminChecking = true
                        viewModel.activateAdmin(adminCode) { ok ->
                            adminChecking = false
                            if (ok) {
                                askAdminCode = false
                            } else {
                                adminError = JournalRu.adminCodeBad
                            }
                        }
                    }
                ) { Text(JournalRu.adminConnect) }
            },
            dismissButton = {
                TextButton(
                    enabled = !adminChecking,
                    onClick = { askAdminCode = false }
                ) { Text(Ru.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSimpleScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
    extra: @Composable () -> Unit = {}
) {
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(title, color = Forest) },
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
                SpeakableText(body) {
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = Forest)
                }
                extra()
                JournalButton(JournalRu.mainMenu, onBack)
            }
        }
    }
}
