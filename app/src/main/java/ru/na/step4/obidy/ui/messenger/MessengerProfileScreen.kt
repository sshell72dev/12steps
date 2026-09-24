package ru.na.step4.obidy.ui.messenger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerProfileScreen(
    viewModel: MessengerViewModel,
    onBack: () -> Unit
) {
    val avatar by viewModel.myAvatarUrl.collectAsStateWithLifecycle()
    val savedName by viewModel.repository.displayName.collectAsStateWithLifecycle()
    var draft by remember(savedName) { mutableStateOf(savedName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadAvatar(uri)
    }
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(MessengerRu.profileTitle, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding)) {
            AtmosphereBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MessengerAvatar(
                    avatarUrl = avatar,
                    title = savedName,
                    size = 112.dp,
                    viewModel = viewModel
                )
                Text(
                    MessengerRu.photoHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                JournalButton(
                    label = if (avatar.isBlank()) MessengerRu.photoAdd else MessengerRu.photoChange,
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    filled = true
                )
                if (avatar.isNotBlank()) {
                    JournalButton(
                        label = MessengerRu.photoDelete,
                        onClick = { viewModel.deleteAvatar() }
                    )
                }
                Text(
                    MessengerRu.nicknameTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest,
                    modifier = Modifier.fillMaxWidth()
                )
                VoiceOutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(MessengerRu.nicknameHint) },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth()) {
                    JournalButton(
                        label = MessengerRu.save,
                        onClick = {
                            val trimmed = draft.trim()
                            if (trimmed.isNotBlank()) viewModel.saveNickname(trimmed)
                        },
                        filled = true
                    )
                }
            }
        }
    }
}
