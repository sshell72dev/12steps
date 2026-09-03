package ru.na.step4.obidy.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.SpeakableText
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

enum class JournalAiMode { HELP, ANALYZE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalAiScreen(
    viewModel: JournalViewModel,
    mode: JournalAiMode,
    entryId: String?,
    onBack: () -> Unit,
    onPro: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    LaunchedEffect(mode, entryId) {
        viewModel.resetAi()
        if (mode == JournalAiMode.HELP) {
            if (viewModel.prepareAiHelp()) viewModel.requestHelp(entryId)
        } else {
            val forceNew = viewModel.consumeAnalyzeForce()
            if (entryId != null && !forceNew && viewModel.showCachedAnalyze(entryId)) {
                return@LaunchedEffect
            }
            if (viewModel.prepareAiHelp()) {
                viewModel.requestAnalyze(entryId, forceRefresh = true)
            }
        }
    }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            mode == JournalAiMode.HELP && entryId != null -> JournalRu.aiHelpEntry
                            mode == JournalAiMode.HELP -> JournalRu.aiHelp
                            else -> JournalRu.aiAnalyze
                        },
                        color = Forest
                    )
                },
                navigationIcon = {
                    AppNavIcon(onBack = {
                        viewModel.resetAi()
                        onBack()
                    })
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.isPro && !state.isAdmin) {
                    Text(JournalRu.proNeededTitle, style = MaterialTheme.typography.titleMedium, color = Forest)
                    Text(JournalRu.proNeededBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    JournalButton("Подробнее о Premium", onPro, filled = false)
                }
                if (!state.isAdmin && state.remainingAi < Int.MAX_VALUE) {
                    val used = JournalPrefs.DAILY_LIMIT - state.remainingAi
                    Text(
                        JournalRu.remainingAi.format(used.coerceAtLeast(0)),
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                when (val ui = ai) {
                    AiUi.Idle, AiUi.Loading -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Forest)
                                Text(JournalRu.aiLoading, color = Forest, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    is AiUi.NeedQuestion -> {
                        Text(JournalRu.questBeforeAi, color = Forest)
                        Text(ui.question.text, style = MaterialTheme.typography.titleMedium, color = Forest)
                        if (ui.question.hint.isNotBlank()) {
                            Text(ui.question.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var custom by remember { mutableStateOf("") }
                        ui.question.options.forEach { option ->
                            JournalButton(option, onClick = {
                                viewModel.answerQuestion(ui.question.id, option)
                                continueAi(viewModel, mode, entryId)
                            })
                        }
                        if (ui.question.options.isEmpty()) {
                            VoiceOutlinedTextField(
                                value = custom,
                                onValueChange = { custom = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )
                            JournalButton(Ru.save, {
                                viewModel.answerQuestion(ui.question.id, custom)
                                continueAi(viewModel, mode, entryId)
                            }, filled = true)
                        }
                        JournalButton(JournalRu.skip, onClick = {
                            viewModel.skipQuestion(ui.question.id)
                            continueAi(viewModel, mode, entryId)
                        })
                        JournalButton(JournalRu.continueWithout, onClick = {
                            viewModel.continueWithoutQuestionnaire()
                            continueAi(viewModel, mode, entryId)
                        })
                    }
                    is AiUi.Ready -> {
                        if (state.isAdmin && ui.prompt.isNotBlank()) {
                            ru.na.step4.obidy.ui.components.AdminPromptBlock(
                                ui.prompt,
                                origin = when {
                                    mode == JournalAiMode.HELP ->
                                        ru.na.step4.obidy.ui.components.PromptOrigin.journalHelp(entryId != null)
                                    else ->
                                        ru.na.step4.obidy.ui.components.PromptOrigin.journalAnalyze(entryId != null)
                                }
                            )
                        }
                        if (ui.fromCache) {
                            Text(
                                if (mode == JournalAiMode.ANALYZE) JournalRu.fromCacheAnalyze
                                else JournalRu.fromCache,
                                color = Amber
                            )
                            if (mode == JournalAiMode.ANALYZE) {
                                JournalButton(JournalRu.aiAnalyzeNew, {
                                    if (viewModel.prepareAiHelp()) {
                                        viewModel.requestAnalyze(entryId, forceRefresh = true)
                                    }
                                })
                            }
                        }
                        SpeakableText(ui.text) {
                            Text(ui.text, style = MaterialTheme.typography.bodyLarge, color = Forest)
                        }
                        if (!ui.portrait.isNullOrBlank()) {
                            if (state.personalityCollectEnabled) {
                                Text(JournalRu.personalityUpdated, color = Amber)
                            } else {
                                JournalButton(JournalRu.updatePersonality, {
                                    viewModel.applyPortrait(ui.portrait)
                                    viewModel.resetAi()
                                    onBack()
                                }, filled = true)
                            }
                        }
                        JournalButton(JournalRu.mainMenu, {
                            viewModel.resetAi()
                            onBack()
                        })
                    }
                    is AiUi.Error -> {
                        Text(ui.message, color = Forest)
                        JournalButton("Ещё раз", {
                            continueAi(viewModel, mode, entryId)
                        }, filled = true)
                        JournalButton(JournalRu.mainMenu, {
                            viewModel.resetAi()
                            onBack()
                        })
                    }
                }
            }
        }
    }
}

private fun continueAi(viewModel: JournalViewModel, mode: JournalAiMode, entryId: String?) {
    if (mode == JournalAiMode.HELP) {
        if (viewModel.prepareAiHelp()) viewModel.requestHelp(entryId)
    } else {
        if (viewModel.prepareAiHelp()) viewModel.requestAnalyze(entryId, forceRefresh = true)
    }
}
