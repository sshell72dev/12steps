package ru.na.step4.obidy.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import ru.na.step4.obidy.data.activity.ActivityEvent
import ru.na.step4.obidy.data.activity.ActivityLog
import ru.na.step4.obidy.data.activity.ActivityRu
import ru.na.step4.obidy.data.activity.primaryTimeline
import ru.na.step4.obidy.data.activity.summarize
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

private enum class ActivityRange { DAY, WEEK, MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityStatsScreen(
    log: ActivityLog,
    onBack: () -> Unit
) {
    var range by remember { mutableStateOf(ActivityRange.DAY) }
    var showAll by remember { mutableStateOf(false) }
    val (from, until) = remember(range) {
        when (range) {
            ActivityRange.DAY -> ActivityLog.dayBounds()
            ActivityRange.WEEK -> ActivityLog.weekBounds()
            ActivityRange.MONTH -> ActivityLog.monthBounds()
        }
    }
    val events by log.observe(from, until).collectAsStateWithLifecycle(emptyList())
    val summary = remember(events) { events.summarize() }
    val visible = remember(events, showAll) {
        if (showAll) events else events.primaryTimeline()
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", ru.na.step4.obidy.data.i18n.I18n.locale()) }
    val dateFmt = remember { SimpleDateFormat("dd.MM HH:mm", ru.na.step4.obidy.data.i18n.I18n.locale()) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ActivityRu.eyebrow, style = MaterialTheme.typography.labelMedium, color = Amber)
                        Text(ActivityRu.title, style = MaterialTheme.typography.titleLarge, color = Forest)
                    }
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeChip(ActivityRu.day, range == ActivityRange.DAY) { range = ActivityRange.DAY }
                    RangeChip(ActivityRu.week, range == ActivityRange.WEEK) { range = ActivityRange.WEEK }
                    RangeChip(ActivityRu.month, range == ActivityRange.MONTH) { range = ActivityRange.MONTH }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeChip(ActivityRu.showMain, !showAll) { showAll = false }
                    RangeChip(ActivityRu.showAll, showAll) { showAll = true }
                }
                StatCard {
                    Text(ActivityRu.results, style = MaterialTheme.typography.labelMedium, color = Amber)
                    Spacer(Modifier.height(8.dp))
                    StatLine(ActivityRu.totalTime, ActivityRu.duration(summary.screenMs))
                    StatLine(ActivityRu.analysisTime, ActivityRu.duration(summary.analysisMs))
                    StatLine(ActivityRu.psychTime, ActivityRu.duration(summary.psychMs))
                    StatLine(ActivityRu.inventoryTime, ActivityRu.duration(summary.inventoryMs))
                    StatLine(ActivityRu.journalTime, ActivityRu.duration(summary.journalMs))
                    StatLine(ActivityRu.listenTime, ActivityRu.duration(summary.listenMs))
                    StatLine(ActivityRu.aiTime, ActivityRu.duration(summary.aiMs))
                    StatLine(ActivityRu.analysisCount, summary.analysisDone.toString())
                    StatLine(ActivityRu.questions, summary.answers.toString())
                    StatLine(ActivityRu.journalCount, summary.journalSaves.toString())
                    StatLine(ActivityRu.psychCount, summary.psychSessions.toString())
                    StatLine(ActivityRu.inventoryCount, summary.inventorySessions.toString())
                    StatLine(ActivityRu.aiCount, summary.aiCalls.toString())
                }
                StatCard {
                    Text(ActivityRu.conclusions, style = MaterialTheme.typography.labelMedium, color = Amber)
                    Spacer(Modifier.height(8.dp))
                    summary.insights.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Forest,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                Text(ActivityRu.timeline, style = MaterialTheme.typography.titleMedium, color = Forest)
                if (visible.isEmpty()) {
                    Text(ActivityRu.empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    visible.forEach { event ->
                        TimelineRow(event, range != ActivityRange.DAY, timeFmt, dateFmt)
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Forest,
            selectedLabelColor = Sand,
            containerColor = SandDeep,
            labelColor = Forest
        )
    )
}

@Composable
private fun StatCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Forest)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Amber)
    }
}

@Composable
private fun TimelineRow(
    event: ActivityEvent,
    showDate: Boolean,
    timeFmt: SimpleDateFormat,
    dateFmt: SimpleDateFormat
) {
    val start = if (showDate) dateFmt.format(Date(event.startedAt)) else timeFmt.format(Date(event.startedAt))
    val endStamp = event.endedAt?.takeIf { it != event.startedAt }?.let { ended ->
        if (showDate) dateFmt.format(Date(ended)) else timeFmt.format(Date(ended))
    }
    val rangeLabel = if (endStamp != null) ActivityRu.fromTo.format(start, endStamp) else start
    val dur = event.durationMs
    val durLabel = if (dur >= 1000L) " · ${ActivityRu.duration(dur)}" else ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Sand.copy(alpha = 0.55f))
            .padding(12.dp)
    ) {
        Text(
            "${ActivityRu.category(event.category)} · ${ActivityRu.type(event.type)}",
            style = MaterialTheme.typography.labelMedium,
            color = Amber
        )
        if (event.label.isNotBlank()) {
            Text(event.label, style = MaterialTheme.typography.bodyLarge, color = Forest)
        }
        Text(
            "$rangeLabel$durLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (event.detail.isNotBlank() && event.detail != event.label) {
            Text(
                event.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Forest,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
