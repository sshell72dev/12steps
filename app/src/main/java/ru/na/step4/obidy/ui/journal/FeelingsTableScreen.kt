package ru.na.step4.obidy.ui.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ru.na.step4.obidy.data.journal.FeelingsTableStore
import ru.na.step4.obidy.data.journal.JournalFieldKind
import ru.na.step4.obidy.data.journal.JournalRu

/**
 * Таблица чувств отдельной страницей: смотреть и искать слова, без записи
 * и без выбора. Вкладка запоминается и открывается при следующем заходе.
 */
@Composable
fun FeelingsTableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FeelingsTableStore(context) }
    var page by remember { mutableIntStateOf(store.page) }
    WordPickerScreen(
        title = JournalRu.feelingsTable,
        kind = JournalFieldKind.FEELINGS,
        selected = emptyList(),
        onPick = {},
        onDictate = {},
        onBack = onBack,
        initialPage = page,
        onPageChange = { value ->
            page = value
            store.page = value
        },
        viewOnly = true
    )
}
