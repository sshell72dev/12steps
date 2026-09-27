package ru.na.step4.obidy.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.book.BookAiClient
import ru.na.step4.obidy.data.book.BookRu
import ru.na.step4.obidy.data.book.applyBookFixes
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

/**
 * Открытая книга: вверху «Главы», ниже текст текущей главы с озвучкой и записью голосом,
 * внизу «Сохранить» и «Новая глава».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    bookId: Long,
    viewModel: BookViewModel,
    onBack: () -> Unit,
    onChapters: () -> Unit,
    onNewChapter: () -> Unit
) {
    val state by viewModel.edit.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<BookFixSession?>(null) }
    var fixing by remember { mutableStateOf(false) }
    var appliedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(bookId) { viewModel.open(bookId) }

    LaunchedEffect(appliedTick) {
        if (appliedTick > 0) snack.showSnackbar(BookRu.fixDone)
    }

    val review = session
    if (review != null) {
        BookFixReview(
            session = review,
            onToggle = { index -> session = review.toggled(index) },
            onAcceptAll = {
                viewModel.updateDraft(applyBookFixes(review.source, review.fixes))
                session = null
                appliedTick += 1
            },
            onAcceptMarked = {
                viewModel.updateDraft(review.preview)
                session = null
                appliedTick += 1
            },
            onCancel = { session = null }
        )
        return
    }

    Scaffold(
        containerColor = Sand,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.book?.title.orEmpty().ifBlank { BookRu.title },
                        color = Forest,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                actions = {
                    TextButton(onClick = onChapters) {
                        Icon(
                            Icons.AutoMirrored.Outlined.List,
                            contentDescription = BookRu.chapters,
                            tint = Forest,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(BookRu.chapters, color = Forest)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.hasChapters) {
                    Text(
                        BookRu.chaptersEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    VoiceOutlinedTextField(
                        value = state.draft,
                        onValueChange = viewModel::updateDraft,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(state.chapter?.title ?: BookRu.chapterText) },
                        minLines = 12,
                        maxLines = 40
                    )
                }
                JournalButton(
                    label = BookRu.save,
                    onClick = {
                        viewModel.saveDraft { ok ->
                            if (ok) scope.launch { snack.showSnackbar(BookRu.saved) }
                        }
                    },
                    filled = true,
                    enabled = state.hasChapters && !state.saving
                )
                JournalButton(
                    label = if (fixing) BookRu.fixChecking else BookRu.fixErrors,
                    onClick = {
                        val text = state.draft
                        fixing = true
                        scope.launch {
                            when (val result = BookAiClient.fixText(text)) {
                                is BookAiClient.Result.Ok ->
                                    session = BookFixSession(source = text, fixes = result.fixes)
                                is BookAiClient.Result.Err -> snack.showSnackbar(result.message)
                            }
                            fixing = false
                        }
                    },
                    enabled = state.hasChapters && state.draft.isNotBlank() && !fixing
                )
                JournalButton(label = BookRu.newChapter, onClick = onNewChapter)
            }
        }
    }
}
