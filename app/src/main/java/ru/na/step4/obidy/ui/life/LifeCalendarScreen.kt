package ru.na.step4.obidy.ui.life

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.life.LifeBoardRu
import ru.na.step4.obidy.data.life.LifeBoardStore
import ru.na.step4.obidy.data.life.LifeItem
import ru.na.step4.obidy.data.life.LifeKind
import ru.na.step4.obidy.data.life.LifeStatus
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

private val monthFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))
private val dayFormat = SimpleDateFormat("d MMMM", Locale("ru"))
private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeCalendarScreen(
    viewModel: LifeBoardViewModel,
    onBack: () -> Unit,
    onActivity: (() -> Unit)? = null
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val events = remember(items) { items.filter { it.kind == LifeKind.EVENT } }
    var monthAnchor by remember { mutableStateOf(startOfMonth(System.currentTimeMillis())) }
    var selectedDay by remember { mutableStateOf(LifeBoardStore.startOfToday()) }
    var dayOpen by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LifeItem?>(null) }
    var viewing by remember { mutableStateOf<LifeItem?>(null) }
    var pendingDelete by remember { mutableStateOf<LifeItem?>(null) }
    val dayEvents = remember(events, selectedDay) {
        events.filter { it.dueAt != null && LifeBoardStore.startOfDay(it.dueAt) == selectedDay }
    }
    val timed = remember(dayEvents) { dayEvents.filter { it.timeSet }.sortedBy { it.dueAt } }
    val untimed = remember(dayEvents) {
        dayEvents.filterNot { it.timeSet }
            .sortedWith(compareBy({ it.status == LifeStatus.DONE }, { it.createdAt }))
    }
    val counts = remember(events) {
        events.mapNotNull { item -> item.dueAt?.let { LifeBoardStore.startOfDay(it) } }
            .groupingBy { it }
            .eachCount()
    }
    // События сегодняшнего дня: со временем — по часам, без времени — по порядку записи.
    val todayEvents = remember(events) {
        val today = LifeBoardStore.startOfToday()
        events.filter { it.dueAt != null && LifeBoardStore.startOfDay(it.dueAt) == today }
            .sortedWith(
                compareBy({ if (it.timeSet) 0 else 1 }, { it.dueAt ?: 0L }, { it.createdAt })
            )
    }

    // Системный «назад» из дня тоже возвращает к календарю, а не закрывает экран.
    BackHandler(enabled = dayOpen) { dayOpen = false }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (dayOpen) dayFormat.format(Date(selectedDay)) else LifeBoardRu.calendar,
                        style = MaterialTheme.typography.titleLarge,
                        color = Forest
                    )
                },
                navigationIcon = {
                    // Стрелка идёт на предыдущий экран: из события — в день, из дня — в календарь.
                    AppNavIcon(
                        onBack = {
                            when {
                                composing || editing != null -> {
                                    composing = false
                                    editing = null
                                }
                                viewing != null -> viewing = null
                                dayOpen -> dayOpen = false
                                else -> onBack()
                            }
                        }
                    )
                },
                actions = {
                    if (onActivity != null) {
                        IconButton(onClick = onActivity) {
                            Icon(
                                Icons.Outlined.Insights,
                                contentDescription = ru.na.step4.obidy.data.activity.ActivityRu.title,
                                tint = Forest
                            )
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
                    contentColor = Sand
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
                    kind = LifeKind.EVENT,
                    initial = editing,
                    onDismiss = { composing = false; editing = null },
                    onSave = { id, title, body, status, dueAt, timeSet ->
                        if (title.isNotBlank() || body.isNotBlank()) {
                            viewModel.save(id, title, body, status, dueAt, timeSet)
                            dueAt?.let { day -> selectedDay = LifeBoardStore.startOfDay(day) }
                            composing = false
                            editing = null
                        }
                    }
                )
            } else if (dayOpen) {
                // Страница дня: календарь убран, видна только круглосуточная шкала.
                LifeDayPlan(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .imePadding(),
                    day = selectedDay,
                    timed = timed,
                    untimed = untimed,
                    onOpen = { viewing = it },
                    onDone = { viewModel.setStatus(it.id, LifeStatus.DONE) },
                    onMove = { id, millis -> viewModel.setTime(id, millis) },
                    onDelete = { pendingDelete = it }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonthHeader(
                        monthAnchor = monthAnchor,
                        onPrev = { monthAnchor = shiftMonth(monthAnchor, -1) },
                        onNext = { monthAnchor = shiftMonth(monthAnchor, 1) }
                    )
                    MonthGrid(
                        monthAnchor = monthAnchor,
                        selectedDay = selectedDay,
                        counts = counts,
                        onSelect = { day ->
                            selectedDay = day
                            dayOpen = true
                        }
                    )
                    if (todayEvents.isNotEmpty()) {
                        TodayEvents(
                            modifier = Modifier.weight(1f),
                            items = todayEvents,
                            onOpen = { viewing = it },
                            onDone = { viewModel.setStatus(it.id, LifeStatus.DONE) }
                        )
                    }
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

    viewing?.let { item ->
        LifeEventView(
            item = item,
            onEdit = {
                viewing = null
                editing = item
            },
            onDismiss = { viewing = null }
        )
    }
}

/** Просмотр события: текст записи и переход в режим редактирования. */
@Composable
private fun LifeEventView(item: LifeItem, onEdit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(item.title, style = MaterialTheme.typography.titleLarge, color = Forest)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    when {
                        !item.timeSet -> LifeBoardRu.withoutTime
                        item.dueAt != null -> "${dayFormat.format(Date(item.dueAt))} · " +
                            timeFormat.format(Date(item.dueAt))
                        else -> LifeBoardRu.withoutTime
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Amber
                )
                if (item.body.isNotBlank()) {
                    RichTextBlock(text = item.body)
                }
                if (item.status == LifeStatus.DONE) {
                    Text(
                        LifeBoardRu.doneMark,
                        style = MaterialTheme.typography.labelLarge,
                        color = Amber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text(LifeBoardRu.edit, color = Forest) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LifeBoardRu.close, color = Forest) }
        }
    )
}

// ---- шапка и сетка месяца ----

@Composable
private fun MonthHeader(monthAnchor: Long, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = Forest)
        }
        Text(
            monthFormat.format(Date(monthAnchor)),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = Forest
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Forest)
        }
    }
}

@Composable
private fun MonthGrid(
    monthAnchor: Long,
    selectedDay: Long,
    counts: Map<Long, Int>,
    onSelect: (Long) -> Unit
) {
    val weeks = remember(monthAnchor) { monthWeeks(monthAnchor) }
    val weekdays = rememberWeekdays()
    val today = remember { LifeBoardStore.startOfToday() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = Forest.copy(alpha = 0.6f)
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    DayCell(
                        modifier = Modifier.weight(1f),
                        day = day,
                        selected = day != null && day == selectedDay,
                        isToday = day != null && day == today,
                        count = day?.let { counts[it] } ?: 0,
                        onClick = { day?.let(onSelect) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    day: Long?,
    selected: Boolean,
    isToday: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val background = when {
        day == null -> Color.Transparent
        selected -> Forest
        isToday -> SandDeep
        else -> SandDeep.copy(alpha = 0.45f)
    }
    val textColor = if (selected) Sand else Forest
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = day != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dayOfMonth(day).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                if (count > 0) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Sand else Amber
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberWeekdays(): List<String> = remember {
    val cal = Calendar.getInstance()
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    val format = SimpleDateFormat("EEEEEE", Locale("ru"))
    (0..6).map { _ ->
        format.format(cal.time).also { cal.add(Calendar.DAY_OF_MONTH, 1) }
    }
}

private fun monthWeeks(monthAnchor: Long): List<List<Long?>> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = monthAnchor
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cursor = Calendar.getInstance()
    cursor.timeInMillis = startOfMonth(monthAnchor)
    val shift = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val cells = MutableList<Long?>(shift) { null }
    (1..daysInMonth).forEach { _ ->
        cells.add(cursor.timeInMillis)
        cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

// ---- события сегодняшнего дня ----











// ---- события сегодняшнего дня ----

@Composable
private fun TodayEvents(
    modifier: Modifier = Modifier,
    items: List<LifeItem>,
    onOpen: (LifeItem) -> Unit,
    onDone: (LifeItem) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            LifeBoardRu.today,
            style = MaterialTheme.typography.titleMedium,
            color = Forest
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) EventDivider(strong = index % 2 == 1)
                TodayEventRow(item = item, onOpen = { onOpen(item) }, onDone = { onDone(item) })
            }
        }
    }
}

/** Разделитель списка: полоса с градиентом идёт через одну. */
@Composable
private fun EventDivider(strong: Boolean) {
    val colors = if (strong) {
        listOf(Color.Transparent, Forest.copy(alpha = 0.55f), Color.Transparent)
    } else {
        listOf(Color.Transparent, Forest.copy(alpha = 0.16f), Color.Transparent)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Brush.horizontalGradient(colors))
    )
}

@Composable
private fun TodayEventRow(item: LifeItem, onOpen: () -> Unit, onDone: () -> Unit) {
    val done = item.status == LifeStatus.DONE
    val past = item.timeSet && (item.dueAt ?: 0L) < System.currentTimeMillis()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (item.timeSet) {
                item.dueAt?.let { timeFormat.format(Date(it)) }.orEmpty()
            } else {
                LifeBoardRu.withoutTime
            },
            modifier = Modifier.width(78.dp),
            style = MaterialTheme.typography.titleSmall,
            color = if (done) Forest.copy(alpha = 0.6f) else Amber
        )
        Text(
            item.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) Forest.copy(alpha = 0.6f) else Forest,
            maxLines = 2
        )
        when {
            done -> Text(
                LifeBoardRu.doneMark,
                style = MaterialTheme.typography.labelLarge,
                color = Amber,
                fontWeight = FontWeight.Bold
            )
            past -> Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Forest.copy(alpha = 0.12f))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    LifeBoardRu.doneAction,
                    style = MaterialTheme.typography.labelLarge,
                    color = Forest
                )
            }
        }
    }
}

// ---- helpers ----

private fun startOfMonth(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun shiftMonth(millis: Long, delta: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.add(Calendar.MONTH, delta)
    return startOfMonth(cal.timeInMillis)
}

private fun dayOfMonth(dayStart: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dayStart
    return cal.get(Calendar.DAY_OF_MONTH)
}


