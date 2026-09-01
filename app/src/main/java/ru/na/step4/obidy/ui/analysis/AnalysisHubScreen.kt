package ru.na.step4.obidy.ui.analysis

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.analysis.AnalysisCatalog
import ru.na.step4.obidy.data.analysis.AnalysisProgressStore
import ru.na.step4.obidy.data.analysis.AnalysisSettings
import ru.na.step4.obidy.data.analysis.AnalysisStreakStore
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.data.profile.ProfileRu
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.data.journal.JournalRu
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisHubScreen(
    settings: AnalysisSettings,
    progress: AnalysisProgressStore,
    streak: AnalysisStreakStore,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit
) {
    val context = LocalContext.current
    val revision by settings.revision.collectAsStateWithLifecycle()
    val progressRevision by progress.revision.collectAsStateWithLifecycle()
    val streakDays by streak.days.collectAsStateWithLifecycle()
    val items = remember(revision) { AnalysisCatalog.hubItems(context, settings) }
    val pausedIds = remember(progressRevision, revision) { progress.pausedCatalogIds() }
    val streakLabel = remember(streakDays) { streak.label(streakDays) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Ru.analysisEyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            Ru.sectionAnalysis,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Forest
                        )
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = Ru.analysisSettings,
                            tint = Forest
                        )
                    }
                    IconButton(onClick = onHistory) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = Ru.analysisHistory,
                            tint = Forest
                        )
                    }
                    IconButton(onClick = onProfile) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = ProfileRu.title,
                            tint = Forest
                        )
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
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    NoteView(NoteIds.ANALYSIS_INTRO, Ru.analysisIntro, Ru.sectionAnalysis)
                    if (streakLabel != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            streakLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = Amber
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                itemsIndexed(items, key = { _, item -> item.first }) { index, item ->
                    val sessionId = AnalysisCatalog.resolveSessionId(item.first, settings)
                    AnalysisMenuRow(
                        number = index + 1,
                        title = item.second,
                        continuing = sessionId in pausedIds,
                        onClick = { onOpen(item.first) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisMenuRow(
    number: Int,
    title: String,
    continuing: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Forest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = Sand
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Forest
            )
            if (continuing) {
                Text(
                    Ru.analysisContinue,
                    style = MaterialTheme.typography.labelMedium,
                    color = Amber
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisSettingsScreen(
    settings: AnalysisSettings,
    prefs: JournalPrefs,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var cleanLong by remember { mutableStateOf(settings.cleanDayLong) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Ru.analysisSettings,
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
            Column(Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Sand.copy(alpha = 0.5f),
                    contentColor = Forest,
                    indicator = { positions ->
                        if (positions.isNotEmpty()) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(positions[tab]),
                                color = Forest
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(JournalRu.settingsGeneral) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(JournalRu.settingsAnalysis) }
                    )
                }
                if (tab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        NoteView(NoteIds.ANALYSIS_SETTINGS, Ru.analysisSettingsHint, Ru.analysisSettings)
                        Spacer(Modifier.height(22.dp))
                        Text(
                            Ru.analysisCleanDayVariant,
                            style = MaterialTheme.typography.titleMedium,
                            color = Forest
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !cleanLong,
                                onClick = {
                                    cleanLong = false
                                    settings.cleanDayLong = false
                                },
                                label = { Text(Ru.analysisCleanDayShort) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Forest,
                                    selectedLabelColor = Sand,
                                    containerColor = SandDeep,
                                    labelColor = Forest
                                )
                            )
                            FilterChip(
                                selected = cleanLong,
                                onClick = {
                                    cleanLong = true
                                    settings.cleanDayLong = true
                                },
                                label = { Text(Ru.analysisCleanDayLong) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Forest,
                                    selectedLabelColor = Sand,
                                    containerColor = SandDeep,
                                    labelColor = Forest
                                )
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            Ru.homeSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Moss
                        )
                    }
                } else {
                    AnalysisCatalogTab(
                        settings = settings,
                        prefs = prefs,
                        onEdit = onEdit
                    )
                }
            }
        }
    }
}
