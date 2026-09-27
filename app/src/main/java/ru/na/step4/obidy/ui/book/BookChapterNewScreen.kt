package ru.na.step4.obidy.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
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
 * Новая глава: название и текст, «Сохранить» закрывает экран,
 * «Новая глава» сохраняет написанное и оставляет поля пустыми для следующей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookChapterNewScreen(
    bookId: Long,
    viewModel: BookViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
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
                text = applyBookFixes(review.source, review.fixes)
                session = null
                appliedTick += 1
            },
            onAcceptMarked = {
                text = review.preview
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
                title = { Text(BookRu.newChapter, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
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
                VoiceOutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(BookRu.chapterTitle) },
                    singleLine = true,
                    speakEnabled = false
                )
                VoiceOutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(BookRu.chapterText) },
                    minLines = 12,
                    maxLines = 40
                )
                JournalButton(
                    label = BookRu.save,
                    onClick = {
                        viewModel.createChapter(bookId, title, text) { id ->
                            if (id != null) onSaved()
                        }
                    },
                    filled = true,
                    enabled = text.isNotBlank()
                )
                JournalButton(
                    label = if (fixing) BookRu.fixChecking else BookRu.fixErrors,
                    onClick = {
                        val source = text
                        fixing = true
                        scope.launch {
                            when (val result = BookAiClient.fixText(source)) {
                                is BookAiClient.Result.Ok ->
                                    session = BookFixSession(source = source, fixes = result.fixes)
                                is BookAiClient.Result.Err -> snack.showSnackbar(result.message)
                            }
                            fixing = false
                        }
                    },
                    enabled = text.isNotBlank() && !fixing
                )
                JournalButton(
                    label = BookRu.newChapter,
                    onClick = {
                        viewModel.createChapter(bookId, title, text) { id ->
                            if (id != null) {
                                title = ""
                                text = ""
                                scope.launch { snack.showSnackbar(BookRu.saved) }
                            }
                        }
                    },
                    enabled = text.isNotBlank()
                )
            }
        }
    }
}
