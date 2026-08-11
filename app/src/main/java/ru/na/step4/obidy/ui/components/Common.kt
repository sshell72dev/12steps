package ru.na.step4.obidy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@Composable
fun AtmosphereBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Forest.copy(alpha = 0.08f),
                    Sand,
                    SandDeep.copy(alpha = 0.55f)
                )
            )
        )
    )
}

@Composable
fun HintIcon(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    var open by remember { mutableStateOf(false) }
    IconButton(
        onClick = { open = true },
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = Ru.hintCd,
            tint = Moss
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(Ru.hintTitle) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(Ru.hintClose)
                }
            }
        )
    }
}

@Composable
fun FieldBlock(
    step: String,
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 3,
    onAssistantClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (step.isNotBlank()) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelMedium,
                color = Amber
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (hint.isNotBlank()) {
                HintIcon(hint)
            }
            if (onAssistantClick != null) {
                IconButton(onClick = onAssistantClick) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver,
                        contentDescription = Ru.assistantCd,
                        tint = Forest
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Forest,
                unfocusedBorderColor = Moss.copy(alpha = 0.35f),
                focusedContainerColor = Sand.copy(alpha = 0.7f),
                unfocusedContainerColor = Sand.copy(alpha = 0.45f),
                cursorColor = Forest
            )
        )
    }
}

@Composable
fun ProgressBar(current: Int, total: Int = Resentment.TOTAL_STEPS) {
    Column {
        Text(
            text = Ru.filled.format(current, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else current / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp)),
            color = Amber,
            trackColor = SandDeep,
            strokeCap = StrokeCap.Round
        )
    }
}

/** Info icon that opens the hint in a dialog. */
@Composable
fun SectionHint(text: String) {
    HintIcon(text)
}
