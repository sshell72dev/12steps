package ru.na.step4.obidy.ui.i18n

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.data.i18n.I18n
import ru.na.step4.obidy.data.i18n.ScreenBundle
import ru.na.step4.obidy.ui.theme.Forest

@Composable
fun EnsureTranslations(
    bundle: ScreenBundle,
    content: @Composable () -> Unit
) {
    val controller = I18n.controller()
    val language by controller?.languageCode?.collectAsStateWithLifecycle(
        initialValue = I18n.languageCode()
    ) ?: remember { mutableStateOf(I18n.languageCode()) }
    val revision by controller?.revision?.collectAsStateWithLifecycle(0)
        ?: remember { mutableStateOf(0) }
    var ready by remember(bundle, language) { mutableStateOf(I18n.isRussian()) }

    LaunchedEffect(bundle, language, revision) {
        val ctrl = I18n.controller()
        if (ctrl == null || ctrl.isRussian()) {
            ready = true
            return@LaunchedEffect
        }
        ready = false
        ctrl.ensureBundle(bundle)
        ready = true
    }

    if (!ready && !I18n.isRussian()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Forest)
        }
    } else {
        content()
    }
}
