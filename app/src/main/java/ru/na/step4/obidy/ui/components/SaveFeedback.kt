package ru.na.step4.obidy.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.na.step4.obidy.Ru

/**
 * Единый отклик на сохранение: всплывашка «Сохранено».
 * Используется application context, поэтому сообщение переживает навигацию назад.
 */
@Composable
fun rememberSavedNotice(): () -> Unit {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        { Toast.makeText(appContext, Ru.saved, Toast.LENGTH_SHORT).show() }
    }
}
