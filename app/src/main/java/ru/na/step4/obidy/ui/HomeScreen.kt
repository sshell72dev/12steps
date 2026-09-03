package ru.na.step4.obidy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.activity.ActivityRu
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.life.LifeBoardRu
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.data.profile.ProfileRu
import ru.na.step4.obidy.data.spiritual.SpiritualRu
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ddTotal: Int,
    onDdStats: () -> Unit,
    onRefreshDd: () -> Unit,
    onSteps: () -> Unit,
    onAnalysis: () -> Unit,
    onPsych: () -> Unit,
    onGoals: () -> Unit,
    onIdeas: () -> Unit,
    onCalendar: () -> Unit,
    onNotes: () -> Unit,
    onActivity: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    showMessenger: Boolean = false,
    onMessenger: () -> Unit = {}
) {
    LaunchedEffect(Unit) { onRefreshDd() }
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Ru.homeEyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            Ru.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Forest
                        )
                    }
                },
                navigationIcon = { AppNavIcon() },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = JournalRu.settings,
                            tint = Forest
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    Ru.homeSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProfileSectionCard(
                    ddTotal = ddTotal,
                    onDdStats = onDdStats,
                    onProfile = onProfile
                )
                SectionCard(
                    title = Ru.sectionSteps,
                    body = Ru.sectionStepsBody,
                    icon = Icons.Outlined.AutoStories,
                    ready = true,
                    onClick = onSteps
                )
                SectionCard(
                    title = Ru.sectionAnalysis,
                    body = Ru.sectionAnalysisBody,
                    icon = Icons.Outlined.SelfImprovement,
                    ready = true,
                    onClick = onAnalysis
                )
                SectionCard(
                    title = Ru.sectionPsych,
                    body = Ru.sectionPsychBody,
                    icon = Icons.Outlined.Psychology,
                    ready = true,
                    onClick = onPsych
                )
                SectionCard(
                    title = ActivityRu.title,
                    body = ActivityRu.homeBody,
                    icon = Icons.Outlined.Insights,
                    ready = true,
                    onClick = onActivity
                )
                if (showMessenger) {
                    SectionCard(
                        title = MessengerRu.title,
                        body = MessengerRu.homeBody,
                        icon = Icons.Outlined.Forum,
                        ready = true,
                        onClick = onMessenger
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompactSectionCard(
                        title = LifeBoardRu.goals,
                        body = LifeBoardRu.goalsBody,
                        icon = Icons.Outlined.Flag,
                        onClick = onGoals,
                        modifier = Modifier.weight(1f)
                    )
                    CompactSectionCard(
                        title = LifeBoardRu.ideas,
                        body = LifeBoardRu.ideasBody,
                        icon = Icons.Outlined.Lightbulb,
                        onClick = onIdeas,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompactSectionCard(
                        title = LifeBoardRu.calendar,
                        body = LifeBoardRu.calendarBody,
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = onCalendar,
                        modifier = Modifier.weight(1f)
                    )
                    CompactSectionCard(
                        title = LifeBoardRu.notes,
                        body = LifeBoardRu.notesBody,
                        icon = Icons.Outlined.Notes,
                        onClick = onNotes,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionCard(
    ddTotal: Int,
    onDdStats: () -> Unit,
    onProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = Forest,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onProfile)
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "${SpiritualRu.abbr} $ddTotal",
                style = MaterialTheme.typography.labelLarge,
                color = Amber,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Sand.copy(alpha = 0.85f))
                    .clickable(onClick = onDdStats)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                Ru.readyBadge,
                style = MaterialTheme.typography.labelMedium,
                color = Amber,
                modifier = Modifier.clickable(onClick = onProfile)
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(modifier = Modifier.clickable(onClick = onProfile)) {
            Text(ProfileRu.title, style = MaterialTheme.typography.titleLarge, color = Forest)
            Spacer(Modifier.height(6.dp))
            Text(
                ProfileRu.homeBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    body: String,
    icon: ImageVector,
    ready: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                if (ready) Ru.readyBadge else Ru.comingSoon,
                style = MaterialTheme.typography.labelMedium,
                color = if (ready) Amber else Moss
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Forest)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactSectionCard(
    title: String,
    body: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Forest, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Forest)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
}
