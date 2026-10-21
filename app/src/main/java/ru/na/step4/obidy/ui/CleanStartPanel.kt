package ru.na.step4.obidy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
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
import ru.na.step4.obidy.ui.theme.Amber
import kotlin.math.roundToInt

private val EdgeSwipeWidth = 36.dp
private val SwipeThreshold = 48.dp
private const val PanelWidthFraction = 0.9f

/**
 * Стартовая страница справа: срок чистого времени и мотивационный текст без пароля.
 * Выезжает смахиванием от правого края, закрывается тапом по фону, крестиком или системным «назад».
 */
@Composable
fun CleanStartHost(content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val openPanel: () -> Unit = { scope.launch { progress.animateTo(1f, tween(260)) } }
    val closePanel: () -> Unit = { scope.launch { progress.animateTo(0f, tween(220)) } }

    Box(Modifier.fillMaxSize()) {
        content()
        CleanStartOverlay(
            progress = progress,
            onOpen = openPanel,
            onClose = closePanel
        )
    }
}

@Composable
private fun CleanStartOverlay(
    progress: Animatable<Float, *>,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val state = remember(progress) { progress.asState() }
    val open = state.value
    var drag by remember { mutableStateOf(0f) }
    val threshold = with(density) { SwipeThreshold.toPx() }

    BackHandler(enabled = open > 0.5f) { onClose() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = maxWidth * PanelWidthFraction
        val panelWidthPx = with(density) { panelWidth.toPx() }

        if (open > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * open))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
            )
        }

        CleanStartPanel(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(panelWidth)
                .fillMaxHeight()
                .offset { IntOffset(((1f - open) * panelWidthPx).roundToInt(), 0) },
            visible = open > 0.5f,
            onClose = onClose
        )

        if (open < 0.5f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(EdgeSwipeWidth)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onHorizontalDrag = { _, delta -> drag += delta },
                            onDragEnd = {
                                if (drag < -threshold) onOpen()
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f }
                        )
                    }
            )
        }
    }
}

@Composable
private fun CleanStartPanel(modifier: Modifier, visible: Boolean, onClose: () -> Unit) {
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
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
            Spacer(Modifier.height(12.dp))
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
