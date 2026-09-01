package ru.na.step4.obidy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.TwelveSteps
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step4HubScreen(
    onBack: () -> Unit,
    onResentments: () -> Unit,
    onComingSoon: (String) -> Unit
) {
    val step = TwelveSteps.byNumber(4)
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Ru.step4HubEyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            Ru.step4HubTitle,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step != null) {
                    Text(
                        step.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Forest
                    )
                }
                Text(
                    Ru.step4HubBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                SectionCard(
                    title = Ru.resentments,
                    body = Ru.inventoryResentmentsBody,
                    icon = Icons.Outlined.SentimentDissatisfied,
                    ready = true,
                    onClick = onResentments
                )
                SectionCard(
                    title = Ru.inventoryFears,
                    body = Ru.comingSoon,
                    icon = Icons.Outlined.HealthAndSafety,
                    ready = false,
                    onClick = { onComingSoon(Ru.inventoryFears) }
                )
                SectionCard(
                    title = Ru.inventorySex,
                    body = Ru.comingSoon,
                    icon = Icons.Outlined.FavoriteBorder,
                    ready = false,
                    onClick = { onComingSoon(Ru.inventorySex) }
                )
                SectionCard(
                    title = Ru.inventoryHarms,
                    body = Ru.comingSoon,
                    icon = Icons.Outlined.PeopleOutline,
                    ready = false,
                    onClick = { onComingSoon(Ru.inventoryHarms) }
                )
            }
        }
    }
}
