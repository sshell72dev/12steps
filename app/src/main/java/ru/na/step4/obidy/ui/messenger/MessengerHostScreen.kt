package ru.na.step4.obidy.ui.messenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.na.step4.obidy.MainActivity
import ru.na.step4.obidy.data.alerts.AppAlerts
import ru.na.step4.obidy.data.messenger.MessengerInvite
import ru.na.step4.obidy.data.messenger.MessengerResult
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.ui.AppNavIcon
import ru.na.step4.obidy.ui.components.AtmosphereBackground
import ru.na.step4.obidy.ui.components.imeScaffoldContent
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand
import ru.na.steps12.voice.ui.VoiceOutlinedTextField

private object MRoutes {
    const val GATE = "gate"
    const val HUB = "hub"
    const val CHAT = "chat/{id}"
    const val QR = "qr"
    const val SCAN = "scan"
    const val GROUP_NEW = "group/new"
    const val GROUP = "group/{id}"
    fun chat(id: String) = "chat/$id"
    fun group(id: String) = "group/$id"
}

@Composable
fun MessengerHostScreen(
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onOpenAlertTarget: (String) -> Unit = {}
) {
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pending by viewModel.repository.pendingInvite.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val nav = rememberNavController()
    val context = LocalContext.current
    val alertsTick = (context as? MainActivity)?.alertsOpenTick ?: 0

    LaunchedEffect(error) {
        val text = error ?: return@LaunchedEffect
        snack.showSnackbar(text)
        viewModel.clearError()
    }

    LaunchedEffect(gate.ready, gate.enabled, pending) {
        if (!gate.enabled || !gate.ready || pending.isNullOrBlank()) return@LaunchedEffect
        val result = viewModel.consumePendingInvite() ?: return@LaunchedEffect
        if (result is MessengerResult.Ok && result.value.chatId.isNotBlank()) {
            val title = result.value.title.ifBlank { MessengerRu.title }
            viewModel.openChat(result.value.chatId, title, result.value.groupId)
            nav.navigate(MRoutes.chat(result.value.chatId))
            snack.showSnackbar(MessengerRu.joined)
        }
    }

    LaunchedEffect(gate.ready, alertsTick) {
        if (!gate.ready || alertsTick == 0) return@LaunchedEffect
        val activity = context as? MainActivity ?: return@LaunchedEffect
        if (!activity.pendingAlertsOpen) return@LaunchedEffect
        if (AppAlerts.isKnownTarget(activity.pendingAlertTarget)) return@LaunchedEffect
        if (!activity.consumePendingAlertsOpen()) return@LaunchedEffect
        if (nav.currentDestination?.route == MRoutes.GATE) {
            nav.navigate(MRoutes.HUB) { popUpTo(MRoutes.GATE) { inclusive = true } }
        }
        viewModel.openChat(AppAlerts.CHAT_ID, MessengerRu.alertsTitle, "")
        nav.navigate(MRoutes.chat(AppAlerts.CHAT_ID)) { launchSingleTop = true }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = MRoutes.GATE) {
            composable(MRoutes.GATE) {
                when {
                    gate.loading -> MessengerLoading(onBack)
                    !gate.enabled -> MessengerDisabledScreen(onBack)
                    gate.needsNickname -> MessengerNicknameScreen(
                        onBack = onBack,
                        onSave = { viewModel.saveNickname(it) }
                    )
                    gate.ready -> LaunchedEffect(Unit) {
                        nav.navigate(MRoutes.HUB) { popUpTo(MRoutes.GATE) { inclusive = true } }
                    }
                    else -> MessengerDisabledScreen(onBack)
                }
            }
            composable(MRoutes.HUB) {
                MessengerHubScreen(
                    viewModel = viewModel,
                    onBack = onBack,
                    onOpenChat = { chat ->
                        viewModel.openChat(chat)
                        nav.navigate(MRoutes.chat(chat.id))
                    },
                    onMyQr = {
                        viewModel.preparePairQr()
                        nav.navigate(MRoutes.QR)
                    },
                    onScan = { nav.navigate(MRoutes.SCAN) },
                    onNewGroup = { nav.navigate(MRoutes.GROUP_NEW) },
                    onJoinChallenge = { key ->
                        viewModel.joinChallenge(key) { created ->
                            if (created != null && created.chatId.isNotBlank()) {
                                viewModel.openChat(
                                    created.chatId,
                                    created.title.ifBlank { MessengerRu.challengeTitle(key, created.title) },
                                    created.groupId
                                )
                                nav.navigate(MRoutes.chat(created.chatId))
                            }
                        }
                    }
                )
            }
            composable(
                MRoutes.CHAT,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                MessengerChatScreen(
                    chatId = id,
                    title = viewModel.chatTitle,
                    groupId = viewModel.chatGroupId,
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                    onGroupInfo = { groupId -> nav.navigate(MRoutes.group(groupId)) },
                    onOpenAlert = { message ->
                        val target = AppAlerts.resolveTarget(
                            message.senderId,
                            message.body,
                            message.senderName
                        )
                        if (target.isNotBlank()) onOpenAlertTarget(target)
                    }
                )
            }
            composable(MRoutes.QR) {
                MessengerQrShowScreen(
                    title = viewModel.qrTitle.ifBlank { MessengerRu.myQrTitle },
                    hint = if (viewModel.qrKind == "group") MessengerRu.groupQr else MessengerRu.myQrHint,
                    uri = if (viewModel.qrKind == "group") {
                        MessengerInvite.groupUri(viewModel.qrToken)
                    } else {
                        MessengerInvite.pairUri(viewModel.qrToken)
                    },
                    canRotate = true,
                    onRotate = {
                        if (viewModel.qrKind == "group" && viewModel.qrGroupId.isNotBlank()) {
                            viewModel.rotateGroupQr(viewModel.qrGroupId)
                        } else {
                            viewModel.rotatePair()
                        }
                    },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(MRoutes.SCAN) {
                MessengerQrScanScreen(
                    onBack = { nav.popBackStack() },
                    onToken = { token ->
                        viewModel.joinToken(token) { result ->
                            if (result is MessengerResult.Ok && result.value.chatId.isNotBlank()) {
                                val title = result.value.title.ifBlank { MessengerRu.title }
                                viewModel.openChat(result.value.chatId, title, result.value.groupId)
                                nav.popBackStack()
                                nav.navigate(MRoutes.chat(result.value.chatId))
                            }
                        }
                    }
                )
            }
            composable(MRoutes.GROUP_NEW) {
                MessengerGroupCreateScreen(
                    contacts = viewModel.contacts,
                    onBack = { nav.popBackStack() },
                    onCreate = { name, ids ->
                        viewModel.createGroup(name, ids) { created ->
                            if (created != null && created.chatId.isNotBlank()) {
                                viewModel.openChat(created.chatId, created.title.ifBlank { name }, created.groupId)
                                nav.popBackStack()
                                nav.navigate(MRoutes.chat(created.chatId))
                            }
                        }
                    }
                )
            }
            composable(
                MRoutes.GROUP,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                MessengerGroupInfoScreen(
                    groupId = id,
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                    onShowQr = { nav.navigate(MRoutes.QR) }
                )
            }
        }
        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessengerLoading(onBack: () -> Unit) {
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(MessengerRu.title, color = Forest) },
                navigationIcon = { AppNavIcon(onBack = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.imeScaffoldContent(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Forest)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerDisabledScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(MessengerRu.disabledTitle, color = Forest) },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(MessengerRu.disabledBody, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerNicknameScreen(
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    Scaffold(
        containerColor = Sand,
        topBar = {
            TopAppBar(
                title = { Text(MessengerRu.nicknameTitle, color = Forest) },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(MessengerRu.nicknameBody, style = MaterialTheme.typography.bodyMedium)
                VoiceOutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(MessengerRu.nicknameHint) },
                    singleLine = true
                )
                JournalButton(
                    label = MessengerRu.continueLabel,
                    onClick = { onSave(name.trim()) },
                    filled = true
                )
            }
        }
    }
}
