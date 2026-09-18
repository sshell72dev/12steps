package ru.na.step4.obidy.ui.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.data.support.SupportBelonging
import ru.na.step4.obidy.data.support.SupportKind
import ru.na.step4.obidy.data.update.ApkInstallResult
import ru.na.step4.obidy.data.update.ApkUpdater
import ru.na.step4.obidy.data.update.UpdateClient
import ru.na.step4.obidy.data.update.UpdateInfo
import ru.na.step4.obidy.data.update.UpdateRu
import ru.na.step4.obidy.data.update.UpdateStore
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import ru.na.step4.obidy.ui.theme.Sand

/**
 * Общее состояние проверки обновления. Живёт вне композиции, поэтому диалог
 * поднимается и при запуске, и по кнопке на экране версии, и из чата.
 */
object UpdateCentre {
    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        _checking.value = true
        try {
            val info = UpdateClient.fetch()
            when {
                info == null -> {
                    _message.value = UpdateRu.checkFailed
                    null
                }
                info.versionCode > BuildConfig.APP_VERSION_CODE -> {
                    UpdateStore(context).lastCheckMs = System.currentTimeMillis()
                    _available.value = info
                    _message.value = null
                    info
                }
                else -> {
                    UpdateStore(context).lastCheckMs = System.currentTimeMillis()
                    _available.value = null
                    _message.value = UpdateRu.upToDate
                    null
                }
            }
        } finally {
            _checking.value = false
        }
    }

    fun postpone(context: Context, info: UpdateInfo) {
        UpdateStore(context).postponedVersionCode = info.versionCode
        _available.value = null
    }

    fun consume() {
        _available.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/**
 * Диалог «Обновить / Отложить». Ставится один раз рядом с NavHost.
 * «Отложить» сохраняет версию локально и кладёт карточку в чат поддержки,
 * чтобы обновиться можно было в любой момент.
 */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by UpdateCentre.available.collectAsStateWithLifecycle()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var awaitingPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val found = UpdateCentre.check(context) ?: return@LaunchedEffect
        if (!found.mandatory && UpdateStore(context).isPostponed(found.versionCode)) {
            UpdateCentre.consume()
        }
    }

    val current = info ?: return

    fun postpone() {
        UpdateCentre.postpone(context, current)
        if (current.mandatory) return
        val app = context.applicationContext as? Step4App ?: return
        scope.launch {
            app.supportRepository.send(
                screen = "${UpdateRu.available} ${current.versionName}",
                route = "update/${current.versionName}",
                body = "${UpdateRu.available} ${current.versionName} (${current.versionCode}). " +
                    "Нажмите «${UpdateRu.openUpdate}», чтобы установить.",
                belonging = SupportBelonging.GENERAL,
                kind = SupportKind.UPDATE
            )
        }
    }

    fun applyInstall() {
        when (ApkUpdater.install(context, ApkUpdater.targetFile(context, current))) {
            ApkInstallResult.STARTED -> Unit
            ApkInstallResult.NEEDS_PERMISSION -> {
                awaitingPermission = true
                ApkUpdater.requestInstallPermission(context)
                error = UpdateRu.permitNeeded
            }
            ApkInstallResult.FAILED -> error = UpdateRu.installFailed
        }
    }

    fun startDownload() {
        if (!ApkUpdater.canInstall(context)) {
            awaitingPermission = true
            ApkUpdater.requestInstallPermission(context)
            error = UpdateRu.permitNeeded
            return
        }
        error = null
        downloading = true
        progress = 0
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                if (ApkUpdater.isReady(context, current)) {
                    ApkUpdater.targetFile(context, current)
                } else {
                    ApkUpdater.download(context, current) { value ->
                        scope.launch { progress = value.coerceAtLeast(0) }
                    }
                }
            }
            downloading = false
            when {
                file == null -> error = UpdateRu.downloadFailed
                else -> applyInstall()
            }
        }
    }

    // Android не сообщает результат из системных настроек, поэтому установку
    // продолжаем сами, как только пользователь вернулся и разрешение выдано.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, current.versionCode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingPermission) {
                awaitingPermission = false
                if (ApkUpdater.canInstall(context)) {
                    if (ApkUpdater.isReady(context, current)) {
                        applyInstall()
                    } else {
                        startDownload()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = {
            if (!downloading && !current.mandatory) postpone()
        },
        title = { Text("${UpdateRu.title} · ${current.versionName}", color = Forest) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${UpdateRu.version}: ${current.versionName} (${current.versionCode})" +
                        sizeLabel(current.sizeBytes),
                    color = Forest,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (current.notes.isNotEmpty()) {
                    Text(
                        UpdateRu.changes,
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium
                    )
                    current.notes.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", color = Forest)
                            Text(
                                item,
                                color = Forest,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                if (downloading) {
                    if (progress > 0) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "${UpdateRu.downloading} · $progress%",
                        color = Forest,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (!error.isNullOrBlank()) {
                    Text(
                        error.orEmpty(),
                        color = Amber,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (current.mandatory) {
                    Text(
                        UpdateRu.mandatory,
                        color = Amber,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { startDownload() },
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Sand)
            ) {
                Text(UpdateRu.updateNow)
            }
        },
        dismissButton = if (current.mandatory) {
            null
        } else {
            {
                TextButton(onClick = { postpone() }, enabled = !downloading) {
                    Text(UpdateRu.postpone)
                }
            }
        }
    )
}

private fun sizeLabel(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        " · ${"%.1f".format(mb)} ${UpdateRu.mb}"
    } else {
        " · ${bytes / 1024} ${UpdateRu.kb}"
    }
}
