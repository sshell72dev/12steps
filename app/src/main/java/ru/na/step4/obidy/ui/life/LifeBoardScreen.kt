package ru.na.step4.obidy.ui.life

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.alerts.AppAlerts
import ru.na.step4.obidy.data.life.LifeBoardRu
import ru.na.step4.obidy.data.life.LifeBoardStore
import ru.na.step4.obidy.data.life.LifeItem
import ru.na.step4.obidy.data.life.LifeKind
import ru.na.step4.obidy.data.life.LifeStatus
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.rememberSavedNotice
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeBoardScreen(
    viewModel: LifeBoardViewModel,
    onBack: () -> Unit,
    onActivity: (() -> Unit)? = null
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val kind = viewModel.kind
    if (kind == LifeKind.EVENT) {
        // События живут в календаре: месяц, день по часам и перетаскивание.
        LifeCalendarScreen(viewModel = viewModel, onBack = onBack, onActivity = onActivity)
        return
    }
    var showDone by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LifeItem?>(null) }
    var composing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LifeItem?>(null) }
    val visible = items
        .filter { if (showDone) it.status == LifeStatus.DONE else it.status == LifeStatus.IN_PROGRESS }
        .let { list ->
            // Идеи и заметки — свежие сверху.
            if (kind == LifeKind.IDEA || kind == LifeKind.NOTE) {
                list.sortedByDescending { it.createdAt.takeIf { t -> t > 0L } ?: it.updatedAt }
            } else {
                list
            }
        }
    val (title, body, empty) = when (kind) {
        LifeKind.GOAL -> Triple(LifeBoardRu.goals, LifeBoardRu.goalsBody, LifeBoardRu.emptyGoals)
        LifeKind.IDEA -> Triple(LifeBoardRu.ideas, LifeBoardRu.ideasBody, LifeBoardRu.emptyIdeas)
        LifeKind.EVENT -> Triple(LifeBoardRu.calendar, LifeBoardRu.calendarBody, LifeBoardRu.emptyEvents)
        else -> Triple(LifeBoardRu.notes, LifeBoardRu.notesBody, LifeBoardRu.emptyNotes)
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = Forest)
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    if (onActivity != null) {
                        IconButton(onClick = onActivity) {
                            Icon(Icons.Outlined.Insights, contentDescription = ru.na.step4.obidy.data.activity.ActivityRu.title, tint = Forest)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        floatingActionButton = {
            if (!composing && editing == null) {
                FloatingActionButton(
                    onClick = { composing = true },
                    containerColor = Forest,
                    contentColor = Sand,
                    modifier = Modifier.padding(end = 72.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = LifeBoardRu.add)
                }
            }
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            if (composing || editing != null) {
                LifeEditor(
                    kind = kind,
                    initial = editing,
                    onDismiss = { composing = false; editing = null },
                    onSave = { id, itemTitle, itemBody, status, dueAt, timeSet, callOn ->
                        if (itemTitle.isNotBlank() || itemBody.isNotBlank()) {
                            viewModel.save(id, itemTitle, itemBody, status, dueAt, timeSet, callOn)
                            composing = false
                            editing = null
                        }
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = Forest.copy(alpha = 0.8f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showDone,
                            onClick = { showDone = false },
                            label = { Text(LifeBoardRu.statusInProgress) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Forest,
                                selectedLabelColor = Sand,
                                containerColor = SandDeep,
                                labelColor = Forest
                            )
                        )
                        FilterChip(
                            selected = showDone,
                            onClick = { showDone = true },
                            label = { Text(LifeBoardRu.statusDone) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Forest,
                                selectedLabelColor = Sand,
                                containerColor = SandDeep,
                                labelColor = Forest
                            )
                        )
                    }
                    if (visible.isEmpty()) {
                        Text(
                            if (showDone) LifeBoardRu.emptyDone else empty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Forest
                        )
                    } else {
                        visible.forEach { item ->
                            LifeCard(
                                item = item,
                                kind = kind,
                                onOpen = { editing = item },
                                onToggleStatus = {
                                    viewModel.setStatus(
                                        item.id,
                                        if (item.status == LifeStatus.DONE) {
                                            LifeStatus.IN_PROGRESS
                                        } else {
                                            LifeStatus.DONE
                                        }
                                    )
                                },
                                onDelete = { pendingDelete = item }
                            )
                        }
                    }
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(LifeBoardRu.deleteTitle, color = Forest) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id)
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
private fun LifeCard(
    item: LifeItem,
    kind: String,
    onOpen: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    // Событие — дата события; идея и заметка — дата и время создания.
    val dateLabel = when {
        kind == LifeKind.EVENT && item.dueAt != null -> formatDate(item.dueAt)
        (kind == LifeKind.IDEA || kind == LifeKind.NOTE) && item.createdAt > 0L ->
            formatDateTime(item.createdAt)
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SandDeep.copy(alpha = 0.78f))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Forest
            )
            Text(
                LifeStatus.label(item.status),
                style = MaterialTheme.typography.labelMedium,
                color = Amber,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleStatus)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        dateLabel?.let { label ->
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Amber)
        }
        if (item.body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Forest.copy(alpha = 0.85f),
                maxLines = 4
            )
        }
        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
            Text(Ru.delete, color = Forest)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LifeEditor(
    kind: String,
    initial: LifeItem?,
    /** День, открытый в календаре: на него падает новая запись. */
    dateDefault: Long? = null,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, body: String, status: String, dueAt: Long?, timeSet: Boolean, callOn: Boolean) -> Unit
) {
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var body by remember(initial?.id) { mutableStateOf(initial?.body.orEmpty()) }
    var status by remember(initial?.id) {
        mutableStateOf(initial?.status ?: LifeStatus.IN_PROGRESS)
    }
    var dueAt by remember(initial?.id) { mutableStateOf(initial?.dueAt ?: dateDefault) }
    var timeSet by remember(initial?.id) { mutableStateOf(initial?.timeSet == true) }
    var timeMinutes by remember(initial?.id) {
        val minutes = initial?.dueAt?.let { millis ->
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = millis
            cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        }
        mutableStateOf(minutes ?: DEFAULT_EVENT_MINUTES)
    }
    var callOn by remember(initial?.id) { mutableStateOf(initial?.callOn == true) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val notifySaved = rememberSavedNotice()
    val hint = when (kind) {
        LifeKind.GOAL -> LifeBoardRu.titleHintGoal
        LifeKind.IDEA -> LifeBoardRu.titleHintIdea
        LifeKind.EVENT -> LifeBoardRu.titleHintEvent
        else -> LifeBoardRu.titleHintNote
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VoiceOutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(LifeBoardRu.title) },
            placeholder = { Text(hint) }
        )
        VoiceOutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(LifeBoardRu.body) },
            placeholder = { Text(LifeBoardRu.bodyHint) },
            minLines = 3
        )
        if (kind == LifeKind.EVENT) {
            JournalButton(
                "${LifeBoardRu.pickDate}${dueAt?.let { ": ${formatDate(it)}" } ?: ""}",
                onClick = { showDate = true }
            )
            JournalButton(
                if (timeSet) "${LifeBoardRu.pickTime}: ${formatMinutes(timeMinutes)}" else LifeBoardRu.withoutTime,
                onClick = { showTime = true }
            )
            if (timeSet) {
                TextButton(onClick = { timeSet = false; callOn = false }) {
                    Text(LifeBoardRu.withoutTime, color = Forest)
                }
                FilterChip(
                    selected = callOn,
                    onClick = {
                        callOn = !callOn
                        if (callOn && Build.VERSION.SDK_INT >= 33 && !AppAlerts.canPost(context)) {
                            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    label = { Text(LifeBoardRu.callAlarm) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Forest,
                        selectedLabelColor = Sand,
                        containerColor = SandDeep,
                        labelColor = Forest
                    )
                )
                Text(
                    LifeBoardRu.callAlarmHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = Forest.copy(alpha = 0.7f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = status == LifeStatus.IN_PROGRESS,
                onClick = { status = LifeStatus.IN_PROGRESS },
                label = { Text(LifeBoardRu.statusInProgress) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Forest,
                    selectedLabelColor = Sand,
                    containerColor = SandDeep,
                    labelColor = Forest
                )
            )
            FilterChip(
                selected = status == LifeStatus.DONE,
                onClick = { status = LifeStatus.DONE },
                label = { Text(LifeBoardRu.statusDone) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Forest,
                    selectedLabelColor = Sand,
                    containerColor = SandDeep,
                    labelColor = Forest
                )
            )
        }
        JournalButton(Ru.save, onClick = {
            notifySaved()
            val day = LifeBoardStore.startOfDay(dueAt ?: LifeBoardStore.startOfToday())
            val eventDue = if (timeSet) day + timeMinutes * 60_000L else day
            onSave(initial?.id, title, body, status, eventDue, timeSet, callOn && timeSet)
        }, filled = true)
        JournalButton(Ru.cancel, onClick = onDismiss)
    }
    if (showTime) {
        val timePicker = rememberTimePickerState(
            initialHour = timeMinutes / 60,
            initialMinute = timeMinutes % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(LifeBoardRu.pickTime, color = Forest) },
            confirmButton = {
                TextButton(onClick = {
                    timeMinutes = timePicker.hour * 60 + timePicker.minute
                    timeSet = true
                    showTime = false
                }) { Text(Ru.save) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text(Ru.cancel) }
            },
            text = { TimePicker(state = timePicker) }
        )
    }
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = dueAt)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dueAt = picker.selectedDateMillis
                    showDate = false
                }) { Text(Ru.save) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(Ru.cancel) }
            }
        ) {
            DatePicker(state = picker)
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(millis))

private fun formatMinutes(minutes: Int): String =
    String.format(Locale("ru"), "%02d:%02d", minutes / 60, minutes % 60)

private const val DEFAULT_EVENT_MINUTES = 9 * 60
