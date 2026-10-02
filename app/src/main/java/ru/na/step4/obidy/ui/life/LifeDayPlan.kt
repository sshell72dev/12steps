package ru.na.step4.obidy.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.life.LifeBoardRu
import ru.na.step4.obidy.data.life.LifeBoardStore
import ru.na.step4.obidy.data.life.LifeItem
import ru.na.step4.obidy.data.life.LifeStatus
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

/** Видимое окно дня: 9:00–21:00, остальные часы — прокруткой. */
private const val VISIBLE_FROM_HOUR = 9
private const val VISIBLE_HOURS = 12
private const val MINUTE_STEP = 10
private const val MINUTE_FINE_STEP = 5

/** Задержка перед разбиением часа — чуть дольше, чтобы не срабатывала случайно. */
private const val HOLD_MS = 800L
private val GRAPH_LEFT_PAD: Dp = 48.dp
private val CARDS_MAX_HEIGHT: Dp = 128.dp

/** Строка одного события: по ней час растёт, чтобы все события были видны. */
private val CHIP_ROW_HEIGHT: Dp = 40.dp

/** Минимальные высоты строк разбитого часа — иначе цифры времени в них не видны. */
private val MINUTE_ROW_MIN: Dp = 24.dp
private val FIVE_ROW_MIN: Dp = 22.dp

/**
 * Строка шкалы: час целиком, раскрытый час — по 10 минут,
 * а десятиминутка, на которую навели событие, — ещё и по 5 минут.
 */
private sealed interface DaySlot {
    val key: String

    data class Hour(val hour: Int) : DaySlot {
        override val key: String = "hour:$hour"
    }

    data class Grain(val hour: Int, val minute: Int, val step: Int) : DaySlot {
        override val key: String = "grain:$hour:$minute:$step"
    }
}

/** Строка шкалы вместе с высотой: час растёт, если внутри несколько событий. */
private data class ScaleLine(val slot: DaySlot, val height: Dp, val items: List<LifeItem>)

private fun scaleLines(
    byHour: Map<Int, List<LifeItem>>,
    zoomHour: Int?,
    zoomMinute: Int?,
    dragId: String?,
    baseHour: Dp,
    minuteHeight: Dp,
    fiveHeight: Dp
): List<ScaleLine> {
    val lines = mutableListOf<ScaleLine>()
    for (hour in 0 until 24) {
        val items = byHour[hour].orEmpty()
        if (zoomHour != hour) {
            lines.add(
                ScaleLine(
                    slot = DaySlot.Hour(hour),
                    // Несколько событий в часе идут друг под другом — час растёт под них все.
                    height = maxOf(baseHour, CHIP_ROW_HEIGHT * items.size + 8.dp),
                    items = items
                )
            )
            continue
        }
        // Раскрытый час: события расходятся по своим десятиминутным графам, а то,
        // что сейчас тащат, остаётся в строке часа — иначе его узел пересоздаётся
        // при каждой смене состава строк и перетаскивание срывается.
        val moving = if (dragId == null) emptyList() else items.filter { it.id == dragId }
        val rest = if (dragId == null) items else items.filter { it.id != dragId }
        lines.add(
            ScaleLine(
                slot = DaySlot.Hour(hour),
                height = if (moving.isEmpty()) {
                    baseHour
                } else {
                    maxOf(baseHour, CHIP_ROW_HEIGHT * moving.size + 8.dp)
                },
                items = moving
            )
        )
        for (step in 0 until 60 / MINUTE_STEP) {
            val minute = step * MINUTE_STEP
            if (zoomMinute == minute) {
                for (part in 0 until MINUTE_STEP / MINUTE_FINE_STEP) {
                    val from = minute + part * MINUTE_FINE_STEP
                    val grain = itemsInGrain(rest, from, MINUTE_FINE_STEP)
                    lines.add(
                        ScaleLine(
                            slot = DaySlot.Grain(hour, from, MINUTE_FINE_STEP),
                            height = maxOf(fiveHeight, CHIP_ROW_HEIGHT * grain.size + 8.dp),
                            items = grain
                        )
                    )
                }
            } else {
                val grain = itemsInGrain(rest, minute, MINUTE_STEP)
                lines.add(
                    ScaleLine(
                        slot = DaySlot.Grain(hour, minute, MINUTE_STEP),
                        height = maxOf(minuteHeight, CHIP_ROW_HEIGHT * grain.size + 8.dp),
                        items = grain
                    )
                )
            }
        }
    }
    return lines
}

/** События, чьи минуты попадают в графу: от [from] длиной [step] минут. */
private fun itemsInGrain(items: List<LifeItem>, from: Int, step: Int): List<LifeItem> {
    val to = from + step
    return items.filter { item ->
        minutesOfDay(item.dueAt ?: 0L) % 60 in from until to
    }
}

private fun hitSlot(lines: List<ScaleLine>, contentY: Float, density: Density): DaySlot? {
    if (contentY < 0f) return null
    var top = 0f
    lines.forEach { line ->
        val height = with(density) { line.height.toPx() }
        if (height > 0f && contentY < top + height) return line.slot
        top += height
    }
    return null
}

/** Верх часа в координатах шкалы — от него начинается перетаскивание события. */
private fun topOfHour(lines: List<ScaleLine>, hour: Int, density: Density): Float {
    var top = 0f
    lines.forEach { line ->
        val slot = line.slot
        if (slot is DaySlot.Hour && slot.hour == hour) return top
        top += with(density) { line.height.toPx() }
    }
    return top
}

@Composable
internal fun LifeDayPlan(
    modifier: Modifier = Modifier,
    day: Long,
    timed: List<LifeItem>,
    untimed: List<LifeItem>,
    onOpen: (LifeItem) -> Unit,
    onDone: (LifeItem) -> Unit,
    onMove: (String, Long) -> Unit,
    onDelete: (LifeItem) -> Unit
) {
    var zoomHour by remember(day) { mutableStateOf<Int?>(null) }
    var zoomMinute by remember(day) { mutableStateOf<Int?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragContentY by remember { mutableStateOf(0f) }
    var hover by remember { mutableStateOf<DaySlot?>(null) }
    var scaleTop by remember { mutableStateOf(0f) }
    var cardsTop by remember { mutableStateOf(0f) }
    var hourPx by remember { mutableStateOf(0f) }
    var lines by remember { mutableStateOf<List<ScaleLine>>(emptyList()) }
    val cardTops = remember { mutableStateMapOf<String, Float>() }
    val cardsScroll = rememberScrollState()
    val scaleScroll = rememberScrollState()
    val density = LocalDensity.current
    val byHour = remember(timed) { timed.groupBy { minutesOfDay(it.dueAt ?: 0L) / 60 } }
    val isToday = day == LifeBoardStore.startOfToday()
    val nowMinutes = currentMinutesOfDay()
    // Событие, которое идёт в данную минуту: последнее начавшееся в текущем часу.
    val currentEvent = if (isToday) {
        byHour[nowMinutes / 60]?.lastOrNull { item ->
            item.timeSet && minutesOfDay(item.dueAt ?: 0L) <= nowMinutes
        }
    } else {
        null
    }

    fun slotUnder(contentY: Float): DaySlot? = hitSlot(lines, contentY, density)

    fun hourTop(hour: Int): Float = topOfHour(lines, hour, density)

    fun resetDrag() {
        dragId = null
        hover = null
        zoomHour = null
        zoomMinute = null
    }

    /** Отмена жеста: время не меняем, только убираем подсветки. */
    fun cancelDrag() = resetDrag()

    fun finishDrag() {
        val movingId = dragId
        val target = hover
        if (movingId != null && target != null) {
            when (target) {
                is DaySlot.Grain -> onMove(
                    movingId,
                    dayStartWithMinutes(day, target.hour * 60 + target.minute)
                )
                is DaySlot.Hour -> onMove(movingId, dayStartWithMinutes(day, target.hour * 60))
            }
        }
        resetDrag()
    }

    // Задержка на часе делит его на десятиминутные графы, а задержка
    // на десятиминутке — ещё и на пятиминутные.
    LaunchedEffect(dragId, hover?.key) {
        val target = hover ?: return@LaunchedEffect
        if (dragId == null) return@LaunchedEffect
        when (target) {
            is DaySlot.Hour -> {
                delay(HOLD_MS)
                zoomHour = target.hour
                zoomMinute = null
            }
            is DaySlot.Grain -> if (target.step == MINUTE_STEP) {
                delay(HOLD_MS)
                zoomMinute = target.minute
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (untimed.isNotEmpty()) {
            Text(
                LifeBoardRu.withoutTime,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Forest.copy(alpha = 0.75f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = CARDS_MAX_HEIGHT)
                    .verticalScroll(cardsScroll)
                    .onGloballyPositioned { cardsTop = it.positionInParent().y },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                untimed.forEach { item ->
                    UntimedCard(
                        item = item,
                        dragging = dragId == item.id,
                        modifier = Modifier
                            .onGloballyPositioned { cardTops[item.id] = it.positionInParent().y }
                            .pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val started = (cardTops[item.id] ?: 0f) +
                                            cardsTop - cardsScroll.value + offset.y
                                        dragId = item.id
                                        dragContentY = started - scaleTop + scaleScroll.value
                                        hover = slotUnder(dragContentY)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragContentY += amount.y
                                        hover = slotUnder(dragContentY)
                                    },
                                    onDragEnd = { finishDrag() },
                                    onDragCancel = { finishDrag() }
                                )
                            },
                        onOpen = { onOpen(item) },
                        onDone = { onDone(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { scaleTop = it.positionInParent().y }
        ) {
            val hourHeight = maxHeight / VISIBLE_HOURS
            val minuteHeight = maxOf(hourHeight * (MINUTE_STEP / 60f), MINUTE_ROW_MIN)
            val fiveHeight = maxOf(minuteHeight / (MINUTE_STEP / MINUTE_FINE_STEP), FIVE_ROW_MIN)
            val rows =
                scaleLines(byHour, zoomHour, zoomMinute, dragId, hourHeight, minuteHeight, fiveHeight)
            val windowPx = with(density) { maxHeight.toPx() }
            SideEffect {
                hourPx = with(density) { hourHeight.toPx() }
                if (lines != rows) lines = rows
            }
            // Первый показ дня: сегодняшний день открываем на текущем событии по центру,
            // остальные дни — с девяти утра.
            var placed by remember(day) { mutableStateOf(false) }
            LaunchedEffect(day, hourPx, lines, placed) {
                if (placed || hourPx <= 0f || lines.isEmpty()) return@LaunchedEffect
                placed = true
                val target = if (isToday) {
                    val hour = nowMinutes / 60
                    val line = lines.firstOrNull { (it.slot as? DaySlot.Hour)?.hour == hour }
                    val rowPx = with(density) { (line?.height ?: hourHeight).toPx() }
                    val index = line?.items?.indexOfFirst { it.id == currentEvent?.id } ?: -1
                    val anchor = if (index >= 0) {
                        val chipPx = with(density) { CHIP_ROW_HEIGHT.toPx() }
                        with(density) { 4.dp.toPx() } + chipPx * index + chipPx / 2f
                    } else {
                        rowPx * ((nowMinutes % 60) / 60f)
                    }
                    hourTop(hour) + anchor - windowPx / 2f
                } else {
                    hourPx * VISIBLE_FROM_HOUR
                }
                scaleScroll.scrollTo(target.toInt().coerceAtLeast(0))
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scaleScroll)
            ) {
                rows.forEach { row ->
                    when (val slot = row.slot) {
                        is DaySlot.Hour -> ScaleHourRow(
                            hour = slot.hour,
                            height = row.height,
                            items = row.items,
                            currentId = currentEvent?.id,
                            highlighted = hover == slot,
                            draggingId = dragId,
                            isToday = isToday,
                            nowMinutes = nowMinutes,
                            onOpen = onOpen,
                            onDone = onDone,
                            onDelete = onDelete,
                            onEventDragStart = { item ->
                                dragId = item.id
                                dragContentY = hourTop(slot.hour) + hourPx / 2f
                                hover = slotUnder(dragContentY)
                            },
                            onEventDrag = { delta ->
                                dragContentY += delta
                                hover = slotUnder(dragContentY)
                            },
                            onEventDragEnd = { finishDrag() },
                            onEventDragCancel = { cancelDrag() }
                        )
                        is DaySlot.Grain -> ScaleMinuteRow(
                            hour = slot.hour,
                            minute = slot.minute,
                            height = row.height,
                            highlighted = hover == slot,
                            items = row.items,
                            onOpen = onOpen
                        )
                    }
                }
            }
            // Призрак перетаскиваемого события: видно, что захват состоялся и куда ведёт.
            val movingItem = dragId?.let { id ->
                (timed + untimed).firstOrNull { it.id == id }
            }
            if (movingItem != null) {
                GhostChip(
                    item = movingItem,
                    target = hover?.let { slot ->
                        when (slot) {
                            is DaySlot.Grain -> formatMinutesOfDay(slot.hour * 60 + slot.minute)
                            is DaySlot.Hour -> formatMinutesOfDay(slot.hour * 60)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = with(density) { (dragContentY - scaleScroll.value).toDp() })
                        .padding(start = GRAPH_LEFT_PAD, end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ScaleHourRow(
    hour: Int,
    height: Dp,
    items: List<LifeItem>,
    currentId: String?,
    highlighted: Boolean,
    draggingId: String?,
    isToday: Boolean,
    nowMinutes: Int,
    onOpen: (LifeItem) -> Unit,
    onDone: (LifeItem) -> Unit,
    onDelete: (LifeItem) -> Unit,
    onEventDragStart: (LifeItem) -> Unit,
    onEventDrag: (Float) -> Unit,
    onEventDragEnd: () -> Unit,
    onEventDragCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (highlighted) Amber.copy(alpha = 0.22f) else SandDeep.copy(alpha = 0.12f))
    ) {
        Text(
            formatMinutesOfDay(hour * 60),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            color = Forest.copy(alpha = 0.8f)
        )
        // Черта текущего времени: рисуется до событий, поэтому ничего не перекрывает.
        if (isToday && nowMinutes / 60 == hour) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = height * ((nowMinutes % 60) / 60f))
                    .height(2.dp)
                    .background(Amber.copy(alpha = 0.45f))
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = GRAPH_LEFT_PAD, top = 4.dp, bottom = 4.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                key(item.id) {
                    EventChip(
                        item = item,
                        emphasis = item.id == currentId,
                        dragging = draggingId == item.id,
                        dragModifier = Modifier.pointerInput(item.id, hour) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onEventDragStart(item) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onEventDrag(amount.y)
                                },
                                onDragEnd = { onEventDragEnd() },
                                onDragCancel = { onEventDragCancel() }
                            )
                        },
                        onOpen = { onOpen(item) },
                        onDone = { onDone(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaleMinuteRow(
    hour: Int,
    minute: Int,
    height: Dp,
    highlighted: Boolean,
    items: List<LifeItem>,
    onOpen: (LifeItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (highlighted) Amber.copy(alpha = 0.35f) else SandDeep.copy(alpha = 0.06f))
    ) {
        Text(
            formatMinutesOfDay(hour * 60 + minute),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Forest.copy(alpha = 0.85f)
        )
        if (items.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = GRAPH_LEFT_PAD, top = 4.dp, bottom = 4.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.forEach { item ->
                    key(item.id) { GrainChip(item = item, onClick = { onOpen(item) }) }
                }
            }
        }
    }
}

/** Призрак перетаскиваемого события: следует за пальцем и показывает целевое время. */
@Composable
private fun GhostChip(item: LifeItem, target: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Forest.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallBell(item = item, modifier = Modifier.padding(end = 6.dp), tint = Amber)
        Text(
            item.title,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            color = Sand,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (target != null) {
            Text(
                "→ $target",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Amber
            )
        }
    }
}

/** Компактный чип события внутри графы: тап открывает событие. */
@Composable
private fun GrainChip(item: LifeItem, onClick: () -> Unit) {
    val done = item.status == LifeStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (done) Forest.copy(alpha = 0.85f) else Sand)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) Sand else Forest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        CallBell(
            item = item,
            modifier = Modifier.padding(start = 6.dp),
            tint = if (done) Sand.copy(alpha = 0.7f) else Amber
        )
    }
}

@Composable
private fun EventChip(
    item: LifeItem,
    emphasis: Boolean,
    dragging: Boolean,
    dragModifier: Modifier,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val done = item.status == LifeStatus.DONE
    val past = (item.dueAt ?: 0L) < System.currentTimeMillis()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    done -> Forest.copy(alpha = 0.85f)
                    dragging -> Amber.copy(alpha = 0.4f)
                    else -> Sand
                }
            )
            .then(dragModifier)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${formatMinutesOfDay(minutesOfDay(item.dueAt ?: 0L))} ${item.title}",
            modifier = Modifier.weight(1f, fill = false),
            style = if (emphasis) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (done) Sand else Forest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        CallBell(
            item = item,
            modifier = Modifier.padding(start = 6.dp),
            tint = if (done) Sand.copy(alpha = 0.7f) else Forest
        )
        if (done) {
            Text(
                LifeBoardRu.doneMark,
                style = MaterialTheme.typography.labelLarge,
                color = Amber,
                fontWeight = FontWeight.Bold
            )
        } else if (past) {
            // Прошедшее событие: заметная кнопка «Сделал».
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Forest.copy(alpha = 0.12f))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    LifeBoardRu.doneAction,
                    style = MaterialTheme.typography.labelLarge,
                    color = Forest
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = Ru.delete,
                tint = if (done) Sand else Forest,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun UntimedCard(
    item: LifeItem,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val done = item.status == LifeStatus.DONE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    done -> Forest.copy(alpha = 0.85f)
                    dragging -> Amber.copy(alpha = 0.35f)
                    else -> SandDeep.copy(alpha = 0.7f)
                }
            )
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
                    style = MaterialTheme.typography.labelLarge,
                    color = Amber,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (!done) {
            Box(
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
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = Ru.delete,
                tint = if (done) Sand else Forest,
                modifier = Modifier.size(18.dp)
            )
        }
    }
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
