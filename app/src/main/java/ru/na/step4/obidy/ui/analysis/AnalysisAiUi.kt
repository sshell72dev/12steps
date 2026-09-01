package ru.na.step4.obidy.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.analysis.AnalysisAnswers
import ru.na.step4.obidy.data.analysis.QaPair
import ru.na.step4.obidy.data.analysis.ReflectionQuestions
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Danger
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep
import ru.na.steps12.voice.ui.SpeakIconButton

fun aiReviewButtonLabel(state: AiReviewUi): String =
    if (state is AiReviewUi.Ready || state is AiReviewUi.Error) Ru.analysisAiReviewAgain
    else Ru.analysisAiReview

@Composable
fun ReflectionActionButton(
    text: String,
    onReflection: (List<String>) -> Unit
) {
    val questions = remember(text) { ReflectionQuestions.extract(text) }
    if (questions.isEmpty()) return
    Button(
        onClick = { onReflection(questions) },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Forest,
            contentColor = Sand
        ),
        shape = RoundedCornerShape(14.dp)
    ) { Text(Ru.analysisReflection) }
}

@Composable
fun ListenAnswersButton(
    answers: List<QaPair>,
    modifier: Modifier = Modifier,
    emphasizeWaiting: Boolean = false
) {
    if (answers.isEmpty()) return
    val speakText = remember(answers) {
        AnalysisAnswers.asSpeakText(
            answers,
            Ru.analysisSpeakQuestion,
            Ru.analysisSpeakAnswer
        )
    }
    if (speakText.isBlank()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            if (emphasizeWaiting) Ru.analysisListenWhileWaiting else Ru.analysisListenAnswers,
            style = MaterialTheme.typography.bodyMedium,
            color = Amber,
            modifier = Modifier.weight(1f)
        )
        SpeakIconButton(text = speakText, tint = Forest)
    }
}

@Composable
fun AiReviewPanel(
    state: AiReviewUi,
    answers: List<QaPair> = emptyList(),
    onRetry: (() -> Unit)? = null
) {
    when (state) {
        AiReviewUi.Idle -> Unit
        AiReviewUi.Loading -> {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Forest,
                        strokeWidth = 2.dp
                    )
                    Text(
                        Ru.analysisAiLoading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Amber
                    )
                }
                ListenAnswersButton(answers = answers, emphasizeWaiting = true)
            }
        }
        is AiReviewUi.Error -> {
            Spacer(Modifier.height(12.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Danger
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(Ru.analysisAiReviewAgain, color = Forest)
                }
            }
        }
        is AiReviewUi.Ready -> {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SandDeep.copy(alpha = 0.72f))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Ru.analysisAiReview,
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest,
                        modifier = Modifier.weight(1f)
                    )
                    SpeakIconButton(text = state.text, tint = Forest)
                }
                if (state.fromCache) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Ru.analysisAiFromCache,
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber
                    )
                }
                if (state.prompt.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    ru.na.step4.obidy.ui.components.AdminPromptBlock(
                        state.prompt,
                        origin = ru.na.step4.obidy.ui.components.PromptOrigin.analysis()
                    )
                }
                Spacer(Modifier.height(8.dp))
                AiReviewText(state.text)
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Ru.analysisAiReviewAgain, color = Forest)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiReviewText(text: String) {
    val heading = Regex("^(?:#{1,3}\\s+|\\*\\*)(.+?)(?:\\*\\*)?\\s*$")
    Column(modifier = Modifier.fillMaxWidth()) {
        text.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trim()
            val match = heading.matchEntire(trimmed)?.takeIf {
                trimmed.startsWith("#") || (trimmed.startsWith("**") && trimmed.endsWith("**"))
            }
            if (match != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    match.groupValues[1].trim('*', ' '),
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest
                )
                Spacer(Modifier.height(4.dp))
            } else if (trimmed.isBlank()) {
                Spacer(Modifier.height(6.dp))
            } else {
                Text(
                    trimmed.replace("**", ""),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}
