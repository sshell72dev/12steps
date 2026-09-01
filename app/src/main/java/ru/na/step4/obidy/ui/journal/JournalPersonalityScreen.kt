package ru.na.step4.obidy.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.notes.NoteIds
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.NoteView
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Moss
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalPersonalityScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var draft by remember(state.personality) { mutableStateOf(state.personality) }
    var showing by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(JournalRu.myPersonality, color = Forest) },
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
                NoteView(NoteIds.JOURNAL_PERSONALITY, JournalRu.personalityHint, JournalRu.myPersonality)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.personalityEnabled) JournalRu.personalityOn else JournalRu.personalityOff,
                        modifier = Modifier.weight(1f),
                        color = Forest
                    )
                    Switch(
                        checked = state.personalityEnabled,
                        onCheckedChange = viewModel::setPersonalityEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = Forest)
                    )
                }
                JournalButton(JournalRu.showPersonality, onClick = { showing = !showing })
                if (showing) {
                    Text(
                        state.personality.ifBlank { JournalRu.personalityEmpty },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Forest
                    )
                }
                if (!editing) {
                    JournalButton(JournalRu.personalityEdit, onClick = { editing = true })
                } else {
                    VoiceOutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Forest,
                            unfocusedBorderColor = Moss.copy(alpha = 0.35f),
                            cursorColor = Forest
                        )
                    )
                    JournalButton(Ru.save, {
                        viewModel.setPersonality(draft)
                        editing = false
                    }, filled = true)
                    JournalButton(Ru.cancel, onClick = { editing = false })
                }
                JournalButton(JournalRu.mainMenu, onBack)
            }
        }
    }
}
