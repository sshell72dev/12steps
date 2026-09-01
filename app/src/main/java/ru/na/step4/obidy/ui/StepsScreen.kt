package ru.na.step4.obidy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.TwelveStep
import ru.na.step4.obidy.data.TwelveSteps
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsScreen(
    onBack: () -> Unit,
    onStep: (TwelveStep) -> Unit
) {
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Ru.stepsEyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            Ru.stepsTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Forest
                        )
                    }
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.imeScaffoldContent(padding)
        ) {
            AtmosphereBackground(Modifier.fillMaxSize())
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        Ru.stepsIntro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }
                items(TwelveSteps.all, key = { it.number }) { step ->
                    StepRow(step = step, onClick = { onStep(step) })
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    step: TwelveStep,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SandDeep.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (step.ready) Forest else Forest.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                step.number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (step.ready) Sand else Forest
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(step.shortTitle, style = MaterialTheme.typography.titleMedium, color = Forest)
            Text(
                if (step.ready) Ru.readyBadge else Ru.comingSoon,
                style = MaterialTheme.typography.labelMedium,
                color = if (step.ready) Amber else Moss
            )
        }
    }
}
