package ru.na.step4.obidy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.data.lock.CleanTimeCalc
import ru.na.step4.obidy.data.lock.LockMoodStore
import ru.na.step4.obidy.data.profile.ProfileQuestionnaire
import ru.na.step4.obidy.data.support.SupportKind
import ru.na.step4.obidy.data.support.SupportRu
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import kotlin.math.roundToInt

private val EdgeSwipeWidth = 36.dp
private val SwipeThreshold = 48.dp
private const val PanelWidthFraction = 1f

/**
 * Стартовая страница справа во весь экран: срок чистого времени и мотивационный текст без пароля.
 * Выезжает смахиванием от правого края, закрывается крестиком или системным «назад».
 * Свайп распознаётся без перекрывающей полосы, поэтому кнопки «Ошибки» и «Идеи» остаются доступны.
 */
@Composable
fun CleanStartHost(
    onFeedback: (String) -> Unit = {},
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val openPanel: () -> Unit = { scope.launch { progress.animateTo(1f, tween(260)) } }
    val closePanel: () -> Unit = { scope.launch { progress.animateTo(0f, tween(220)) } }

    Box(Modifier.fillMaxSize()) {
        content()
        CleanStartOverlay(
            progress = progress,
            onOpen = openPanel,
            onClose = closePanel,
            onFeedback = onFeedback
        )
    }
}

@Composable
private fun CleanStartOverlay(
    progress: Animatable<Float, *>,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { SwipeThreshold.toPx() }
    val state = remember(progress) { progress.asState() }
    val open = state.value

    BackHandler(enabled = open > 0.5f) { onClose() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = maxWidth * PanelWidthFraction
        val panelWidthPx = with(density) { panelWidth.toPx() }

        if (open > 0f) {
            CleanStartPanel(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(((1f - open) * panelWidthPx).roundToInt(), 0) },
                visible = open > 0.5f,
                onClose = onClose,
                onFeedback = onFeedback
            )
        }

        // Полоса свайпа — только у правого края и без перекрытия остального экрана.
        // До порога события не забираются, поэтому кнопки под полосой нажимаются.
        if (open < 0.5f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(EdgeSwipeWidth)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var opened = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed || change.isConsumed) break
                                val dragged = change.position.x - down.position.x
                                if (!opened && dragged < -swipeThreshold) {
                                    opened = true
                                    onOpen()
                                }
                                if (opened) change.consume()
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun CleanStartPanel(
    modifier: Modifier,
    visible: Boolean,
    onClose: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Step4App
    val profile by app.profileStore.snapshot.collectAsStateWithLifecycle()
    val lastUse = profile.answers[ProfileQuestionnaire.ID_LAST_USE].orEmpty()
    val displayName = profile.name.ifBlank {
        profile.answers[ProfileQuestionnaire.ID_NAME].orEmpty()
    }.trim()
    val clean = remember(lastUse) { CleanTimeCalc.of(lastUse) }
    val mood = remember { LockMoodStore(context) }
    val sessionSeed = remember { System.currentTimeMillis() }
    val photo = remember(clean?.days, sessionSeed) {
        mood.pickBackground(clean?.days, sessionSeed)
    }
    var quote by remember { mutableStateOf(mood.cachedQuote()) }

    LaunchedEffect(visible) { if (visible) quote = mood.refresh() }

    Box(modifier) {
        LockPhoto(photo)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Прокручивается только содержимое, кнопки и крестик закреплены внизу:
            // так панель закрывается с любого экрана.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Glass)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (displayName.isNotBlank()) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = OnPhoto,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    Ru.lockYouAreClean,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = OnPhoto,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                if (clean != null) {
                    Text(
                        clean.periodLine,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Amber,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        clean.totalLine,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        color = OnPhoto.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        CleanTimeCalc.unknownMessage(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 23.sp
                        ),
                        color = OnPhoto.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    quote,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        lineHeight = 27.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = OnPhoto,
                    textAlign = TextAlign.Center
                )
            }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                OutlinedButton(
                    onClick = {
                        onClose()
                        onFeedback(SupportKind.IDEA)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OnPhoto.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnPhoto)
                ) {
                    Text(
                        SupportRu.ideas,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = {
                        onClose()
                        onFeedback(SupportKind.BUG)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor = Forest
                    )
                ) {
                    Text(
                        SupportRu.report,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                }
                }
                Spacer(Modifier.height(8.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        tint = OnPhoto,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
