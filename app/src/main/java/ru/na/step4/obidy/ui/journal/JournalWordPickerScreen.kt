package ru.na.step4.obidy.ui.journal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.journal.EmotionCatalog
import ru.na.step4.obidy.data.journal.JournalFieldKind
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.journal.WordColumn
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun JournalWordPickerScreen(
    viewModel: JournalViewModel,
    fieldId: String,
    kind: JournalFieldKind,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val field = state.fields.find { it.id == fieldId }
    val title = when (kind) {
        JournalFieldKind.FEELINGS -> JournalRu.pickFeelings
        JournalFieldKind.THOUGHTS -> JournalRu.pickThoughts
        JournalFieldKind.TEXT -> field?.title.orEmpty()
    }
    WordPickerScreen(
        title = title,
        kind = kind,
        selected = EmotionCatalog.selectedWords(state.fieldValues[fieldId].orEmpty(), kind),
        onToggle = { viewModel.toggleFieldWord(fieldId, it) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WordPickerScreen(
    title: String,
    kind: JournalFieldKind,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit
) {
    val columns = EmotionCatalog.columns(kind)
    var query by remember { mutableStateOf("") }
    val pagerState = rememberPagerState(pageCount = { columns.size })
    val scope = rememberCoroutineScope()
    val q = query.trim()
    val searching = q.isNotBlank()

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(title, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(JournalRu.pickSearch) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = Forest)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Forest,
                        unfocusedBorderColor = Moss.copy(alpha = 0.35f),
                        focusedContainerColor = Sand.copy(alpha = 0.7f),
                        unfocusedContainerColor = Sand.copy(alpha = 0.45f),
                        cursorColor = Forest
                    )
                )
                Text(
                    JournalRu.pickSelected.format(selected.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = Forest
                )
                if (searching) {
                    val grouped = EmotionCatalog.allWords(kind)
                        .filter { (_, word) -> word.contains(q, ignoreCase = true) }
                        .groupBy({ it.first }, { it.second })
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        grouped.forEach { (column, words) ->
                            item(key = "h-${column.id}") {
                                Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                                    Text(
                                        column.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Forest,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        words.forEach { word ->
                                            WordChip(
                                                word = word,
                                                selected = selected.any { it.equals(word, ignoreCase = true) },
                                                onClick = { onToggle(word) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Sand.copy(alpha = 0.5f),
                        contentColor = Forest,
                        edgePadding = 0.dp,
                        indicator = { positions ->
                            if (positions.isNotEmpty()) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(positions[pagerState.currentPage]),
                                    color = Forest
                                )
                            }
                        }
                    ) {
                        columns.forEachIndexed { index, column ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(column.title) }
                            )
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        WordColumnPage(
                            column = columns[page],
                            selected = selected,
                            onToggle = onToggle
                        )
                    }
                }
                JournalButton(JournalRu.pickApply, onBack, filled = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordColumnPage(
    column: WordColumn,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Text(
            column.title,
            style = MaterialTheme.typography.titleLarge,
            color = Forest,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            column.words.forEach { word ->
                WordChip(
                    word = word,
                    selected = selected.any { it.equals(word, ignoreCase = true) },
                    onClick = { onToggle(word) }
                )
            }
        }
    }
}

@Composable
private fun WordChip(
    word: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(word) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Forest,
            selectedLabelColor = Sand,
            containerColor = Sand.copy(alpha = 0.85f),
            labelColor = Forest
        )
    )
}
