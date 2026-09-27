package ru.na.step4.obidy.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import ru.na.step4.obidy.data.book.BookChapter
import ru.na.step4.obidy.data.book.BookRu
import ru.na.step4.obidy.data.i18n.I18n
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalCard
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

/** Список всех глав книги: выбор главы открывает её текст в редакторе. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookChaptersScreen(
    bookId: Long,
    viewModel: BookViewModel,
    onBack: () -> Unit,
    onPick: (Long) -> Unit
) {
    val state by viewModel.edit.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) { viewModel.open(bookId) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(BookRu.chapters, color = Forest)
                        Text(
                            state.book?.title.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            if (state.chapters.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        BookRu.chaptersEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.chapters, key = { it.id }) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            selected = chapter.id == state.chapterId,
                            onClick = {
                                viewModel.selectChapter(chapter.id)
                                onPick(chapter.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: BookChapter, selected: Boolean, onClick: () -> Unit) {
    JournalCard(onClick = onClick) {
        Text(
            chapter.title.ifBlank { BookRu.chapterText },
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Amber else Forest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            chapter.preview.ifBlank { BookRu.emptyText },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            SimpleDateFormat("dd.MM.yyyy HH:mm", I18n.locale()).format(Date(chapter.updatedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
