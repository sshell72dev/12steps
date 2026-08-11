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
import ru.na.step4.obidy.ui.components.AtmosphereBackground
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = Ru.back,
                            tint = Forest
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AtmosphereBackground(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
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

                GuideBlock(number = "01", title = Ru.g1t, body = Ru.g1b)
                GuideBlock(number = "02", title = Ru.g2t, body = Ru.g2b)
                GuideBlock(number = "03", title = Ru.g3t, body = Ru.g3b)
                GuideBlock(number = "04", title = Ru.g4t, body = Ru.g4b)
                GuideBlock(number = "05", title = Ru.g5t, body = Ru.g5b)
                GuideBlock(number = "06", title = Ru.g6t, body = Ru.g6b)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GuideBlock(number: String, title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelMedium,
            color = Amber
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Forest
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
