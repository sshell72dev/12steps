package ru.na.step4.obidy.ui.spiritual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.na.step4.obidy.data.spiritual.SpiritualEconomy
import ru.na.step4.obidy.data.spiritual.SpiritualRatingStore
import ru.na.step4.obidy.data.spiritual.SpiritualRu
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.step4.obidy.ui.theme.SandDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiritualStatsScreen(
    store: SpiritualRatingStore,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { store.refreshMissPenalties() }
    val snap by store.snapshot.collectAsState()
    val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", ru.na.step4.obidy.data.i18n.I18n.locale())

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            SpiritualRu.abbr,
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                        Text(
                            SpiritualRu.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Forest
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = Forest
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SandDeep.copy(alpha = 0.72f))
                        .padding(18.dp)
                ) {
                    Text(
                        SpiritualRu.total,
                        style = MaterialTheme.typography.labelMedium,
                        color = Amber
                    )
                    Text(
                        snap.totalScore.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = Forest
                    )
                    Spacer(Modifier.height(10.dp))
                    StatLine(SpiritualRu.day, "${snap.dayScore} · ${snap.dayLabel}")
                    StatLine(
                        SpiritualRu.rate,
                        "×" + String.format(Locale.US, "%.2f", snap.rate)
                    )
                    StatLine(SpiritualRu.practiceStreak, snap.practiceStreak.toString())
                    StatLine(SpiritualRu.missWindow, snap.missStreak.toString())
                }

                Text(
                    SpiritualRu.events,
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest
                )
                if (snap.recent.isEmpty()) {
                    Text(
                        SpiritualRu.emptyEvents,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    snap.recent.forEach { event ->
                        val sign = if (event.delta > 0) "+" else ""
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SandDeep.copy(alpha = 0.55f))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    SpiritualEconomy.sourceLabel(event.source),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Forest
                                )
                                Text(
                                    "$sign${event.delta}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (event.delta >= 0) Amber else Moss
                                )
                            }
                            if (event.reason.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    event.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                dateFmt.format(Date(event.at)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Moss
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Moss)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Forest)
    }
}
