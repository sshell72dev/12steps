package ru.na.step4.obidy.ui.life

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
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

private val HOUR_HEIGHT: Dp = 56.dp
private val GAP_HEIGHT: Dp = 44.dp
private val EVENT_HEIGHT: Dp = 64.dp
private val MINUTE_HEIGHT: Dp = 30.dp
private const val MINUTES_IN_DAY = 24 * 60
private const val HOLD_MS = 450L
private const val EVENT_SPAN_MINUTES = 30

private val monthFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))
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
    var composing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LifeItem?>(null) }
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

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(LifeBoardRu.calendar, style = MaterialTheme.typography.titleLarge, color = Forest)
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
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
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        onSelect = { day -> selectedDay = day }
                    )
                    Text(
                        LifeBoardRu.dragHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = Forest.copy(alpha = 0.7f)
                    )
                    DayPlan(
                        day = selectedDay,
                        timed = timed,
                        untimed = untimed,
                        onOpen = { editing = it },
                        onDone = { viewModel.setStatus(it.id, LifeStatus.DONE) },
                        onMove = { id, millis -> viewModel.setTime(id, millis) },
                        onSwap = { first, second -> viewModel.swapTimes(first, second) },
                        onDelete = { pendingDelete = it }
                    )
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

// ---- день ----

private sealed interface DayRow {
    val key: String
    data class Event(val item: LifeItem) : DayRow {
        override val key: String = "event:${item.id}"
    }
    data class Gap(val id: String, val from: Int, val to: Int) : DayRow {
        override val key: String = "gap:$id"
    }
    data class Hour(val gapId: String, val hour: Int) : DayRow {
        override val key: String = "hour:$gapId:$hour"
    }
    data class Minute(val gapId: String, val hour: Int, val minute: Int) : DayRow {
        override val key: String = "minute:$gapId:$hour:$minute"
    }
}

private fun rowHeight(row: DayRow): Dp = when (row) {
    is DayRow.Event -> EVENT_HEIGHT
    is DayRow.Gap -> GAP_HEIGHT
    is DayRow.Hour -> HOUR_HEIGHT
    is DayRow.Minute -> MINUTE_HEIGHT
}

/** Пустые часы дня держим одной строкой «от и до», пока их не раскрыли перетаскиванием. */
private fun buildRows(timed: List<LifeItem>, expandedGap: String?, zoomHour: Int?): List<DayRow> {
    val rows = mutableListOf<DayRow>()
    var cursor = 0
    timed.forEachIndexed { index, item ->
        val start = minutesOfDay(item.dueAt ?: 0L)
        if (start > cursor) {
            rows.addAll(gapRows("gap$index-$cursor", cursor, start, expandedGap, zoomHour))
        }
        rows.add(DayRow.Event(item))
        cursor = maxOf(cursor, start + EVENT_SPAN_MINUTES)
    }
    if (cursor < MINUTES_IN_DAY) {
        rows.addAll(gapRows("gapTail-$cursor", cursor, MINUTES_IN_DAY, expandedGap, zoomHour))
    }
    return rows
}

private fun gapRows(
    id: String,
    from: Int,
    to: Int,
    expandedGap: String?,
    zoomHour: Int?
): List<DayRow> {
    if (expandedGap != id) return listOf(DayRow.Gap(id, from, to))
    val rows = mutableListOf<DayRow>()
    var hour = from / 60
    val lastHour = maxOf(to - 1, from) / 60
    while (hour <= lastHour) {
        rows.add(DayRow.Hour(id, hour))
        if (zoomHour == hour) {
            (0 until 6).forEach { step -> rows.add(DayRow.Minute(id, hour, step * 10)) }
        }
        hour++
    }
    return rows
}

private fun hitRow(rows: List<DayRow>, y: Float, density: Density): DayRow? {
    val gap = with(density) { 6.dp.toPx() }
    var top = 0f
    rows.forEach { row ->
        val height = with(density) { rowHeight(row).toPx() }
        if (y >= top && y <= top + height) return row
        top += height + gap
    }
    return null
}

@Composable
private fun DayPlan(
    day: Long,
    timed: List<LifeItem>,
    untimed: List<LifeItem>,
    onOpen: (LifeItem) -> Unit,
    onDone: (LifeItem) -> Unit,
    onMove: (String, Long) -> Unit,
    onSwap: (String, String) -> Unit,
    onDelete: (LifeItem) -> Unit
) {
    var expandedGap by remember(day) { mutableStateOf<String?>(null) }
    var zoomHour by remember(day) { mutableStateOf<Int?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    var hover by remember { mutableStateOf<DayRow?>(null) }
    val density = LocalDensity.current
    val rows = remember(timed, expandedGap, zoomHour) { buildRows(timed, expandedGap, zoomHour) }
    val isToday = day == LifeBoardStore.startOfToday()
    val nowMinutes = currentMinutesOfDay()

    // Удержание над свободным временем раскрывает часы, удержание на часе — по 10 минут.
    LaunchedEffect(dragId, hover?.key) {
        val target = hover
        if (dragId == null || target == null) return@LaunchedEffect
        when (target) {
            is DayRow.Gap -> {
                delay(HOLD_MS)
                expandedGap = target.id
            }
            is DayRow.Hour -> {
                delay(HOLD_MS)
                zoomHour = target.hour
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .pointerInput(rows, day) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val hit = hitRow(rows, offset.y, density)
                        if (hit is DayRow.Event) {
                            dragId = hit.item.id
                            dragY = offset.y
                            hover = hit
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragY += amount.y
                        hover = hitRow(rows, dragY, density)
                    },
                    onDragEnd = {
                        val target = hover
                        val movingId = dragId
                        if (movingId != null && target != null) {
                            when (target) {
                                is DayRow.Minute -> onMove(
                                    movingId,
                                    dayStartWithMinutes(day, target.hour * 60 + target.minute)
                                )
                                is DayRow.Hour -> onMove(
                                    movingId,
                                    dayStartWithMinutes(day, target.hour * 60)
                                )
                                is DayRow.Event -> if (target.item.id != movingId) {
                                    onSwap(movingId, target.item.id)
                                }
                                is DayRow.Gap -> Unit
                            }
                        }
                        dragId = null
                        hover = null
                        expandedGap = null
                        zoomHour = null
                    },
                    onDragCancel = {
                        dragId = null
                        hover = null
                        expandedGap = null
                        zoomHour = null
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (timed.isEmpty()) {
            Text(
                LifeBoardRu.emptyDay,
                style = MaterialTheme.typography.bodyMedium,
                color = Forest.copy(alpha = 0.8f)
            )
        }
        rows.forEach { row ->
            when (row) {
                is DayRow.Event -> EventRow(
                    item = row.item,
                    dragging = dragId == row.item.id,
                    swapTarget = dragId != null && hover == row && dragId != row.item.id,
                    onOpen = { onOpen(row.item) },
                    onDone = { onDone(row.item) },
                    onDelete = { onDelete(row.item) }
                )
                is DayRow.Gap -> GapRow(
                    from = row.from,
                    to = row.to,
                    highlighted = hover == row,
                    nowMinutes = if (isToday && nowMinutes in row.from until row.to) nowMinutes else null
                )
                is DayRow.Hour -> HourRow(hour = row.hour, highlighted = hover == row)
                is DayRow.Minute -> MinuteRow(
                    hour = row.hour,
                    minute = row.minute,
                    highlighted = hover == row
                )
            }
        }
        if (untimed.isNotEmpty()) {
            Text(
                LifeBoardRu.withoutTime,
                style = MaterialTheme.typography.labelLarge,
                color = Forest.copy(alpha = 0.75f)
            )
            untimed.forEach { item ->
                UntimedRow(
                    item = item,
                    onOpen = { onOpen(item) },
                    onDone = { onDone(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

// ---- строки дня ----

@Composable
private fun EventRow(
    item: LifeItem,
    dragging: Boolean,
    swapTarget: Boolean,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val done = item.status == LifeStatus.DONE
    val past = item.timeSet && (item.dueAt ?: 0L) < System.currentTimeMillis()
    val background = when {
        done -> Forest.copy(alpha = 0.85f)
        dragging -> Amber.copy(alpha = 0.35f)
        swapTarget -> Amber.copy(alpha = 0.2f)
        else -> SandDeep.copy(alpha = 0.8f)
    }
    val contentColor = if (done) Sand else Forest
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EVENT_HEIGHT)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 2
            )
            val time = item.dueAt?.let { timeFormat.format(Date(it)) }.orEmpty()
            Text(
                if (done) "${LifeBoardRu.doneMark} · $time" else time,
                style = MaterialTheme.typography.labelSmall,
                color = if (done) Sand.copy(alpha = 0.85f) else Amber,
                fontWeight = if (done) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (past && !done) {
            TextButton(onClick = onDone) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Forest)
                Spacer(Modifier.size(4.dp))
                Text(LifeBoardRu.doneAction, color = Forest)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = contentColor)
        }
    }
}

@Composable
private fun GapRow(from: Int, to: Int, highlighted: Boolean, nowMinutes: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(GAP_HEIGHT)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) Amber.copy(alpha = 0.25f) else SandDeep.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "${formatMinutesOfDay(from)} – ${formatMinutesOfDay(to)}",
            style = MaterialTheme.typography.labelMedium,
            color = Forest.copy(alpha = 0.7f)
        )
        nowMinutes?.let { minutes ->
            // Временная черта: где сейчас проходит время.
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(Amber)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    formatMinutesOfDay(minutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber
                )
            }
        }
    }
}

@Composable
private fun HourRow(hour: Int, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) Amber.copy(alpha = 0.3f) else SandDeep.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            formatMinutesOfDay(hour * 60),
            style = MaterialTheme.typography.labelMedium,
            color = Forest
        )
    }
}

@Composable
private fun MinuteRow(hour: Int, minute: Int, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MINUTE_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) Amber.copy(alpha = 0.35f) else SandDeep.copy(alpha = 0.14f))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            formatMinutesOfDay(hour * 60 + minute),
            style = MaterialTheme.typography.labelSmall,
            color = Forest.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun UntimedRow(
    item: LifeItem,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val done = item.status == LifeStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (done) Forest.copy(alpha = 0.85f) else SandDeep.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (done) Sand else Forest,
                maxLines = 2
            )
            if (done) {
                Text(
                    LifeBoardRu.doneMark,
                    style = MaterialTheme.typography.labelSmall,
                    color = Sand.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (!done) {
            TextButton(onClick = onDone) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Forest)
                Spacer(Modifier.size(4.dp))
                Text(LifeBoardRu.doneAction, color = Forest)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = Ru.delete,
                tint = if (done) Sand else Forest
            )
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

private fun minutesOfDay(millis: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

private fun currentMinutesOfDay(): Int = minutesOfDay(System.currentTimeMillis())

private fun dayStartWithMinutes(day: Long, minutes: Int): Long = day + minutes * 60_000L

private fun formatMinutesOfDay(minutes: Int): String =
    String.format(Locale("ru"), "%02d:%02d", minutes / 60, minutes % 60)
