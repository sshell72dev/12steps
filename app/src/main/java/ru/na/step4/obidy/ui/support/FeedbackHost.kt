package ru.na.step4.obidy.ui.support

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.support.SupportBelonging
import ru.na.step4.obidy.data.support.SupportClient
import ru.na.step4.obidy.data.support.SupportKind
import ru.na.step4.obidy.data.support.SupportRepository
import ru.na.step4.obidy.data.support.SupportRu
import ru.na.step4.obidy.data.support.SupportScreens
import ru.na.step4.obidy.data.support.SupportTicket
import ru.na.step4.obidy.data.support.SupportTopic
import ru.na.step4.obidy.data.support.SupportTopicStore
import ru.na.step4.obidy.ui.components.isImeVisible
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@Composable
fun FeedbackHost(
    repository: SupportRepository,
    route: String?,
    modifier: Modifier = Modifier
) {
    val unread by repository.unread.collectAsStateWithLifecycle()
    var openKind by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (isActive) {
            repository.refreshUnread()
            delay(45_000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(8f)
    ) {
        if (openKind == null) {
            DraggableFeedbackFabs(
                unread = unread,
                onIdea = { openKind = SupportKind.IDEA },
                onBug = { openKind = SupportKind.BUG }
            )
        } else {
            ReportOverlay(
                repository = repository,
                route = route,
                kind = openKind!!,
                onDismiss = { openKind = null },
                onSendReport = { screen, routeValue, body, belonging ->
                    val kind = openKind ?: SupportKind.BUG
                    openKind = null
                    scope.launch {
                        repository.send(screen, routeValue, body, belonging, kind)
                    }
                },
                onSendReply = { ticketId, reply ->
                    openKind = null
                    scope.launch {
                        repository.reply(ticketId, reply)
                    }
                }
            )
        }
    }
}

private class FabSpotStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSaved(): Boolean = prefs.contains(KEY_X)

    fun x(): Float = prefs.getFloat(KEY_X, 1f)

    fun y(): Float = prefs.getFloat(KEY_Y, 1f)

    fun save(x: Float, y: Float) {
        prefs.edit()
            .putFloat(KEY_X, x.coerceIn(0f, 1f))
            .putFloat(KEY_Y, y.coerceIn(0f, 1f))
            .apply()
    }

    companion object {
        private const val PREFS = "feedback_fabs"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
    }
}

@Composable
private fun DraggableFeedbackFabs(
    unread: Int,
    onIdea: () -> Unit,
    onBug: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val store = remember { FabSpotStore(context) }
    val pad = with(density) { 12.dp.toPx() }
    val defaultBottom = with(density) { 88.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        var clusterW by remember { mutableFloatStateOf(with(density) { 56.dp.toPx() }) }
        var clusterH by remember { mutableFloatStateOf(with(density) { 152.dp.toPx() }) }
        var dragging by remember { mutableStateOf(false) }
        var x by remember { mutableFloatStateOf(Float.NaN) }
        var y by remember { mutableFloatStateOf(Float.NaN) }

        fun clampTo(px: Float, py: Float): Pair<Float, Float> {
            val maxX = (maxW - clusterW - pad).coerceAtLeast(pad)
            val maxY = (maxH - clusterH - pad).coerceAtLeast(pad)
            return px.coerceIn(pad, maxX) to py.coerceIn(pad, maxY)
        }

        fun defaultPos(): Pair<Float, Float> {
            val dx = (maxW - clusterW - pad).coerceAtLeast(pad)
            val dy = (maxH - clusterH - defaultBottom).coerceAtLeast(pad)
            return clampTo(dx, dy)
        }

        fun fromSaved(): Pair<Float, Float> {
            if (!store.hasSaved()) return defaultPos()
            val rangeX = (maxW - clusterW - 2 * pad).coerceAtLeast(1f)
            val rangeY = (maxH - clusterH - 2 * pad).coerceAtLeast(1f)
            return clampTo(pad + store.x() * rangeX, pad + store.y() * rangeY)
        }

        fun persist() {
            val rangeX = (maxW - clusterW - 2 * pad).coerceAtLeast(1f)
            val rangeY = (maxH - clusterH - 2 * pad).coerceAtLeast(1f)
            store.save((x - pad) / rangeX, (y - pad) / rangeY)
        }

        fun moveBy(dx: Float, dy: Float) {
            val curX = if (x.isNaN()) fromSaved().first else x
            val curY = if (y.isNaN()) fromSaved().second else y
            val next = clampTo(curX + dx, curY + dy)
            x = next.first
            y = next.second
        }

        LaunchedEffect(maxW, maxH, clusterW, clusterH, dragging) {
            if (dragging || maxW <= 0f || clusterW <= 0f) return@LaunchedEffect
            val pos = fromSaved()
            x = pos.first
            y = pos.second
        }

        val start = fromSaved()
        val shownX = if (x.isNaN()) start.first else x
        val shownY = if (y.isNaN()) start.second else y

        Column(
            modifier = Modifier
                .offset { IntOffset(shownX.roundToInt(), shownY.roundToInt()) }
                .onGloballyPositioned { coords ->
                    val w = coords.size.width.toFloat()
                    val h = coords.size.height.toFloat()
                    if (w > 0f && kotlin.math.abs(w - clusterW) > 1f) clusterW = w
                    if (h > 0f && kotlin.math.abs(h - clusterH) > 1f) clusterH = h
                }
                .graphicsLayer {
                    scaleX = if (dragging) 1.05f else 1f
                    scaleY = if (dragging) 1.05f else 1f
                    alpha = if (dragging) 0.92f else 1f
                }
                .pointerInput(maxW, maxH, clusterW, clusterH) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragged = false
                        val slop = viewConfiguration.touchSlop
                        var total = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            total += delta
                            if (!dragged && total.getDistance() > slop) {
                                dragged = true
                                dragging = true
                            }
                            if (dragged) {
                                change.consume()
                                moveBy(delta.x, delta.y)
                            }
                        }
                        if (dragged) {
                            val curX = if (x.isNaN()) shownX else x
                            val curY = if (y.isNaN()) shownY else y
                            val c = clampTo(curX, curY)
                            x = c.first
                            y = c.second
                            persist()
                            dragging = false
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = SupportRu.moveFabs,
                tint = Forest.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp)
            )
            FloatingActionButton(
                onClick = onIdea,
                containerColor = SandDeep,
                contentColor = Forest,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = SupportRu.ideasCd)
            }
            FloatingActionButton(
                onClick = onBug,
                containerColor = Forest,
                contentColor = Sand,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (unread > 0) {
                            Badge(containerColor = Amber, contentColor = Forest) {
                                Text(if (unread > 9) "9+" else unread.toString())
                            }
                        }
                    }
                ) {
                    Icon(Icons.Outlined.BugReport, contentDescription = SupportRu.reportCd)
                }
            }
        }
    }
}

@Composable
private fun ReportOverlay(
    repository: SupportRepository,
    route: String?,
    kind: String,
    onDismiss: () -> Unit,
    onSendReport: (screen: String, route: String, body: String, belonging: String) -> Unit,
    onSendReply: (ticketId: Long, reply: String) -> Unit
) {
    val context = LocalContext.current
    val screen = SupportScreens.title(route)
    val scope = rememberCoroutineScope()
    val isIdea = kind == SupportKind.IDEA
    var body by remember(kind) { mutableStateOf("") }
    var belonging by remember(kind) { mutableStateOf(SupportBelonging.SCREEN) }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var opened by remember { mutableStateOf<SupportTicket?>(null) }
    var reply by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf(false) }
    val topicStore = remember { SupportTopicStore(context) }
    var topicOrder by remember { mutableStateOf(SupportTopic.all) }

    suspend fun reloadMine() {
        val bundle = repository.mineBundle()
        mine = bundle.tickets.filter { SupportKind.normalize(it.kind) == kind }
        topicOrder = SupportTopic.sorted(
            SupportTopic.merge(topicStore.counts(), bundle.topicCounts, bundle.tickets)
        )
    }

    LaunchedEffect(kind) {
        reloadMine()
    }

    fun submit() {
        if (sending) return
        val current = opened
        if (current == null) {
            if (body.isBlank()) return
            val text = body
            val mode = belonging
            if (SupportTopic.isTopic(mode)) topicStore.bump(mode)
            sending = true
            onSendReport(screen, route.orEmpty(), text, mode)
        } else {
            if (reply.isBlank()) return
            val text = reply
            sending = true
            onSendReply(current.id, text)
        }
    }

    val imeVisible = isImeVisible()
    val canSend = if (opened == null) body.isNotBlank() else reply.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(8f)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (!sending) onDismiss()
            },
        contentAlignment = if (imeVisible) Alignment.BottomCenter else Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* keep open */ }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    when {
                        opened != null -> if (isIdea) SupportRu.myIdeas else SupportRu.myReports
                        isIdea -> SupportRu.ideaTitle
                        else -> SupportRu.report
                    },
                    color = Forest,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (imeVisible) 220.dp else 420.dp)
                        .verticalScroll(rememberScrollState())
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val current = opened
                    if (current == null) {
                        MetaLine(
                            SupportRu.screen,
                            SupportBelonging.resolve(belonging, route, screen).first
                        )
                        MetaLine(SupportRu.user, repository.userName)
                        MetaLine(SupportRu.date, SupportClient.nowLabel())
                        Text(SupportRu.belonging, color = Forest, style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SupportBelonging.choosableFor(kind).forEach { mode ->
                                FilterChip(
                                    selected = belonging == mode,
                                    onClick = { belonging = mode },
                                    label = { Text(SupportBelonging.label(mode)) },
                                    enabled = !sending,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Forest,
                                        selectedLabelColor = Sand,
                                        containerColor = SandDeep,
                                        labelColor = Forest
                                    )
                                )
                            }
                        }
                        if (!isIdea) {
                            Text(
                                SupportRu.topic,
                                color = Forest,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                topicOrder.forEach { mode ->
                                    FilterChip(
                                        selected = belonging == mode,
                                        onClick = { belonging = mode },
                                        label = { Text(SupportBelonging.label(mode)) },
                                        enabled = !sending,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Forest,
                                            selectedLabelColor = Sand,
                                            containerColor = SandDeep,
                                            labelColor = Forest
                                        )
                                    )
                                }
                            }
                        }
                        VoiceOutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(if (isIdea) SupportRu.ideaBody else SupportRu.body)
                            },
                            placeholder = {
                                Text(if (isIdea) SupportRu.ideaBodyHint else SupportRu.bodyHint)
                            },
                            minLines = if (imeVisible) 2 else 3,
                            enabled = !sending
                        )
                        if (!notice.isNullOrBlank()) {
                            Text(notice.orEmpty(), color = Amber)
                        }
                        if (mine.isNotEmpty() && !imeVisible) {
                            Text(
                                if (isIdea) SupportRu.myIdeas else SupportRu.myReports,
                                color = Forest,
                                style = MaterialTheme.typography.titleSmall
                            )
                            mine.forEach { ticket ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Sand.copy(alpha = 0.7f))
                                        .clickable {
                                            scope.launch {
                                                val fresh = repository.open(ticket.id) ?: ticket
                                                opened = fresh
                                                mine = mine.map { if (it.id == fresh.id) fresh else it }
                                                reply = ""
                                                notice = null
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    val mark = if (!ticket.userRead) " · ${SupportRu.unread}" else ""
                                    Text(
                                        "${ticket.screen}$mark",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Forest
                                    )
                                    Text(
                                        "${SupportRu.belonging}: ${ticket.belongingLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber
                                    )
                                    Text(
                                        "${SupportRu.status}: ${ticket.statusLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber
                                    )
                                    Text(
                                        ticket.preview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Forest,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    } else {
                        TextButton(onClick = {
                            opened = null
                            scope.launch { reloadMine() }
                        }) { Text("← ${SupportRu.backToList}") }
                        if (!imeVisible) {
                            TicketPreview(
                                ticket = current,
                                showUser = false,
                                onCopyMessage = { msg -> copyPlain(context, msg.body) },
                                onShareMessage = { msg -> sharePlain(context, msg.body) },
                                onCopyTicket = { copyPlain(context, SupportClient.shareText(current)) },
                                onShareTicket = { sharePlain(context, SupportClient.shareText(current)) },
                                onDeleteTicket = { pendingDelete = true }
                            )
                        }
                        VoiceOutlinedTextField(
                            value = reply,
                            onValueChange = { reply = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(SupportRu.reply) },
                            placeholder = { Text(SupportRu.replyUserHint) },
                            minLines = 2,
                            enabled = !sending
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))
                if (imeVisible) {
                    Button(
                        enabled = !sending && canSend,
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Forest,
                            contentColor = Sand
                        )
                    ) {
                        Text(
                            if (opened == null) SupportRu.send else SupportRu.replySend,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TextButton(
                        enabled = !sending,
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(Ru.cancel) }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(enabled = !sending, onClick = onDismiss) { Text(Ru.cancel) }
                        TextButton(
                            enabled = !sending && canSend,
                            onClick = { submit() }
                        ) {
                            Text(if (opened == null) SupportRu.send else SupportRu.replySend)
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete && opened != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text(SupportRu.deleteTicket, color = Forest) },
            confirmButton = {
                TextButton(onClick = {
                    val id = opened?.id ?: return@TextButton
                    pendingDelete = false
                    scope.launch {
                        if (repository.deleteTicket(id)) {
                            opened = null
                            reloadMine()
                        }
                    }
                }) { Text(SupportRu.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) { Text(Ru.cancel) }
            }
        )
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Amber)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Forest)
    }
}
