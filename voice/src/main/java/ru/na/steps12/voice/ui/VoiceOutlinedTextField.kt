package ru.na.steps12.voice.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    voiceEnabled: Boolean = true,
    speakEnabled: Boolean = true,
    appendDictation: Boolean = true
) {
    val showVoice = (voiceEnabled || speakEnabled) && !readOnly
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var field by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    var fieldHeightPx by remember { mutableFloatStateOf(0f) }
    var fieldWidthPx by remember { mutableFloatStateOf(0f) }
    var scrollToEndTick by remember { mutableIntStateOf(0) }
    var pendingRevealEnd by remember { mutableStateOf(false) }

    SideEffect {
        if (value != field.text) {
            val wasAtEnd = field.selection.max >= field.text.length
            val selection = if (wasAtEnd) {
                TextRange(value.length)
            } else {
                TextRange(
                    field.selection.start.coerceIn(0, value.length),
                    field.selection.end.coerceIn(0, value.length)
                )
            }
            field = TextFieldValue(text = value, selection = selection)
        }
    }

    LaunchedEffect(scrollToEndTick) {
        if (!pendingRevealEnd && scrollToEndTick == 0) return@LaunchedEffect
        if (!pendingRevealEnd) return@LaunchedEffect
        repeat(6) {
            runCatching { focusRequester.requestFocus() }
            delay(40)
            field = field.copy(selection = TextRange(field.text.length))
            revealBottom(bringIntoViewRequester, fieldWidthPx, fieldHeightPx, with(density) { 160.dp.toPx() })
        }
        pendingRevealEnd = false
    }

    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            field = next
            if (next.text != value) onValueChange(next.text)
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged {
                fieldHeightPx = it.height.toFloat()
                fieldWidthPx = it.width.toFloat()
            },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = if (showVoice || trailingIcon != null) {
            {
                Row {
                    trailingIcon?.invoke()
                    VoiceFieldActions(
                        value = field.text,
                        onValueChange = { next ->
                            onValueChange(next)
                            runCatching { focusRequester.requestFocus() }
                        },
                        enabled = enabled,
                        voiceEnabled = voiceEnabled && !readOnly,
                        speakEnabled = speakEnabled,
                        append = appendDictation,
                        selection = field.selection,
                        onDictated = { spoken, at ->
                            val next = mergeSpokenField(
                                field.copy(selection = at),
                                spoken,
                                appendDictation
                            )
                            field = next
                            onValueChange(next.text)
                            pendingRevealEnd = true
                            scrollToEndTick += 1
                        },
                        extra = null
                    )
                }
            }
        } else {
            null
        },
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}

@OptIn(ExperimentalFoundationApi::class)
private suspend fun revealBottom(
    bringIntoViewRequester: BringIntoViewRequester,
    widthPx: Float,
    heightPx: Float,
    stripPx: Float
) {
    if (heightPx > 0f && widthPx > 0f) {
        val strip = min(heightPx, stripPx)
        bringIntoViewRequester.bringIntoView(
            Rect(0f, heightPx - strip, widthPx, heightPx)
        )
    } else {
        bringIntoViewRequester.bringIntoView()
    }
}
