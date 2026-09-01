package ru.na.step4.obidy.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.data.app.Changelog
import ru.na.step4.obidy.data.app.ReleaseNote
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val releases = remember { Changelog.load(context) }
    val currentVersion = BuildConfig.APP_VERSION_NAME
    val currentCode = BuildConfig.APP_VERSION_CODE

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(JournalRu.versionMenu, color = Forest) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Forest.copy(alpha = 0.08f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            JournalRu.versionCurrent,
                            color = Amber,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            "$currentVersion ($currentCode)",
                            color = Forest,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    JournalRu.versionHistory,
                    color = Amber,
                    style = MaterialTheme.typography.labelMedium
                )
                releases.forEachIndexed { index, release ->
                    ReleaseCard(
                        release = release,
                        isCurrent = release.version == currentVersion && release.versionCode == currentCode,
                        initiallyExpanded = index == 0
                    )
                }
                JournalButton(JournalRu.mainMenu, onBack)
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseNote,
    isCurrent: Boolean,
    initiallyExpanded: Boolean
) {
    var expanded by remember(release.versionCode) { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Forest.copy(alpha = 0.12f) else Sand.copy(alpha = 0.7f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCurrent) {
                            "${release.version} (${release.versionCode}) · ${JournalRu.versionInstalled}"
                        } else {
                            "${release.version} (${release.versionCode})"
                        },
                        color = Forest,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (release.date.isNotBlank()) {
                        Text(
                            release.date,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Forest
                )
            }
            if (expanded) {
                release.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("•", color = Forest)
                        Text(item, color = Forest, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
