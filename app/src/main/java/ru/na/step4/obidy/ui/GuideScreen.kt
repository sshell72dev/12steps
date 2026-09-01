package ru.na.step4.obidy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Ru.guideTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Forest
                    )
                },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.imeScaffoldContent(padding)
        ) {
            AtmosphereBackground(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = Ru.guideEyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    color = Amber
                )
                Text(
                    text = Ru.guideQuote,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Forest
                )
                Text(
                    text = Ru.guideIntro,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GuideBlock(
                    number = "I",
                    title = InventoryStructure.INTRO_TITLE,
                    body = InventoryStructure.INTRO
                )
                GuideBlock(
                    number = "А",
                    title = InventoryStructure.POINT_A,
                    body = InventoryStructure.POINT_A_BODY
                )
                GuideBlock(
                    number = "Б",
                    title = InventoryStructure.POINT_B,
                    body = InventoryStructure.POINT_B_BODY + "\n\n" +
                        InventoryStructure.questionsGuideText(1, 4)
                )
                GuideBlock(
                    number = "В",
                    title = InventoryStructure.POINT_V,
                    body = InventoryStructure.POINT_V_BODY + "\n\n" +
                        InventoryStructure.questionsGuideText(5, 12)
                )
                GuideBlock(
                    number = "Г",
                    title = InventoryStructure.POINT_G,
                    body = InventoryStructure.POINT_G_BODY
                )
                GuideBlock(number = "05", title = Ru.g5t, body = Ru.g5b)
                GuideBlock(number = "06", title = Ru.g6t, body = Ru.g6b)

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GuideBlock(number: String, title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Forest
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
