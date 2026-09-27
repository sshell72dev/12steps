package ru.na.step4.obidy.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import ru.na.step4.obidy.data.book.Book
import ru.na.step4.obidy.data.book.BookRu
import ru.na.step4.obidy.data.i18n.I18n
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalCard
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

/** Стартовая страница раздела: книги пользователя и «плюс» для новой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    viewModel: BookViewModel,
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(BookRu.title, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    draftTitle = ""
                    adding = true
                },
                containerColor = Forest,
                contentColor = Sand
            ) {
                Icon(Icons.Outlined.Add, BookRu.addBook)
            }
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            if (books.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        BookRu.empty,
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
                    item {
                        Text(
                            BookRu.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(books, key = { it.id }) { book ->
                        BookRow(book = book, onClick = { onOpenBook(book.id) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(BookRu.addBook, color = Forest) },
            text = {
                VoiceOutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(BookRu.bookTitle) },
                    placeholder = { Text(BookRu.bookTitleHint) },
                    singleLine = true,
                    speakEnabled = false
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draftTitle.isNotBlank(),
                    onClick = {
                        adding = false
                        viewModel.createBook(draftTitle) { id ->
                            if (id != null) onOpenBook(id)
                        }
                    }
                ) { Text(BookRu.create) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text(BookRu.cancel) }
            }
        )
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    JournalCard(onClick = onClick) {
        Text(
            book.title.ifBlank { BookRu.title },
            style = MaterialTheme.typography.titleMedium,
            color = Forest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            listOf(
                BookRu.chaptersCount.format(book.chapterCount),
                updatedLabel(book.updatedAt)
            ).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun updatedLabel(at: Long): String {
    if (at <= 0L) return ""
    return SimpleDateFormat("dd.MM.yyyy HH:mm", I18n.locale()).format(Date(at))
}
