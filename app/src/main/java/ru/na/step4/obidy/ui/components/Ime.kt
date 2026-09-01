package ru.na.step4.obidy.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Scaffold content that shrinks to the keyboard so lists can scroll down to it
 * and sticky action bars stay above the IME.
 */
fun Modifier.imeScaffoldContent(padding: PaddingValues): Modifier =
    this
        .fillMaxSize()
        .padding(padding)
        .consumeWindowInsets(padding)
        .imePadding()

@OptIn(ExperimentalLayoutApi::class)
fun Modifier.navigationBarsPaddingIfImeHidden(): Modifier = composed {
    if (WindowInsets.isImeVisible) this else navigationBarsPadding()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isImeVisible(): Boolean = WindowInsets.isImeVisible
