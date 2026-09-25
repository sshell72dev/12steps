package ru.na.step4.obidy.ui.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.backup.BackupAutoWorker
import ru.na.step4.obidy.data.backup.BackupManager
import ru.na.step4.obidy.data.backup.BackupManifest
import ru.na.step4.obidy.data.backup.BackupProgress
import ru.na.step4.obidy.data.backup.BackupRu
import ru.na.step4.obidy.data.backup.BackupServer
import ru.na.step4.obidy.data.backup.ServerSlot
import ru.na.step4.obidy.ui.journal.JournalButton
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Раздел «Резервная копия» в настройках журнала: локальный файл с паролем,
 * откат последнего восстановления и копия на сервере.
 */
@Composable
fun BackupSettingsPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<BackupProgress?>(null) }
    var canRollback by remember { mutableStateOf(BackupManager.rollbackAvailable(context)) }

    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var confirmUri by remember { mutableStateOf<Uri?>(null) }
    var manifest by remember { mutableStateOf<BackupManifest?>(null) }
    var password by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf<CharArray?>(null) }

    var email by remember { mutableStateOf(BackupServer.email(context)) }
    var linked by remember { mutableStateOf(BackupServer.isLinked(context)) }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var slot by remember { mutableStateOf<ServerSlot?>(null) }

    fun report(text: String?) {
        notice = text
    }

    fun runExport(uri: Uri, pwd: CharArray?) {
        scope.launch {
            busy = true
            progress = null
            val outcome = BackupManager.export(context, uri, pwd) { progress = it }
            busy = false
            progress = null
            report(if (outcome.ok) BackupRu.savedOk else BackupRu.savedError)
        }
    }

    fun openRestore(uri: Uri, pwd: CharArray?) {
        scope.launch {
            busy = true
            val encrypted = BackupManager.isEncrypted(context, uri)
            if (encrypted && pwd == null) {
                busy = false
                importUri = uri
                return@launch
            }
            val found = BackupManager.readManifest(context, uri, pwd)
            busy = false
            if (found == null) {
                report(BackupRu.restoredError)
                return@launch
            }
            if (found.packageName.isNotEmpty() && found.packageName != context.packageName) {
                report(BackupRu.manifestOtherApp)
                return@launch
            }
            restorePassword = pwd
            manifest = found
            confirmUri = uri
        }
    }

    fun applyRestore(uri: Uri, pwd: CharArray?) {
        scope.launch {
            busy = true
            progress = null
            canRollback = BackupManager.saveRollback(context)
            val outcome = BackupManager.restoreFromUri(context, uri, pwd) { progress = it }
            busy = false
            progress = null
            when {
                outcome.ok -> {
                    Toast.makeText(context, BackupRu.restoredOk, Toast.LENGTH_LONG).show()
                    BackupManager.restartApp(context)
                }
                outcome.detail == "password" -> report(BackupRu.passwordWrong)
                else -> report(BackupRu.restoredError)
            }
        }
    }

    fun applyRollback() {
        scope.launch {
            busy = true
            progress = null
            val outcome = BackupManager.restoreFromFile(context, BackupManager.rollbackFile(context), null) {
                progress = it
            }
            busy = false
            progress = null
            if (outcome.ok) {
                Toast.makeText(context, BackupRu.rollbackDone, Toast.LENGTH_LONG).show()
                BackupManager.restartApp(context)
            } else {
                report(BackupRu.rollbackMissing)
            }
        }
    }

    val saveBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            exportUri = uri
            password = ""
        }
    }
    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) openRestore(uri, null)
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(BackupRu.title, color = Amber, style = MaterialTheme.typography.labelMedium)
        Text(
            BackupRu.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        JournalButton(
            label = BackupRu.export,
            onClick = { saveBackup.launch(BackupManager.suggestedFileName()) },
            filled = true,
            enabled = !busy
        )
        JournalButton(
            label = BackupRu.restore,
            onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            enabled = !busy
        )
        if (canRollback && !busy) {
            JournalButton(label = BackupRu.rollbackAction, onClick = { applyRollback() })
        }
        if (busy) {
            Text(BackupRu.busy, color = Forest, style = MaterialTheme.typography.bodySmall)
        }
        progress?.let { state ->
            Text(
                "${BackupRu.progressLabel}: ${state.records} · " +
                    "${BackupManager.humanSize(state.bytes)} / ${BackupManager.humanSize(state.totalBytes)}",
                color = Forest,
                style = MaterialTheme.typography.bodySmall
            )
        }
        reportNotice(notice)
    }

    if (exportUri != null) {
        PasswordDialog(
            title = BackupRu.passwordTitle,
            hint = BackupRu.passwordHint,
            value = password,
            onValueChange = { password = it },
            allowEmpty = true,
            onDismiss = { exportUri = null },
            onConfirm = { usePassword ->
                val uri = exportUri
                exportUri = null
                if (uri != null) runExport(uri, usePassword?.toCharArray())
            }
        )
    }

    if (importUri != null) {
        PasswordDialog(
            title = BackupRu.passwordEnter,
            hint = BackupRu.passwordHint,
            value = password,
            onValueChange = { password = it },
            allowEmpty = false,
            onDismiss = { importUri = null },
            onConfirm = { usePassword ->
                val uri = importUri
                importUri = null
                if (uri != null) openRestore(uri, usePassword?.toCharArray())
            }
        )
    }

    if (confirmUri != null) {
        val info = manifest
        val sizeBytes = info?.let { fileSizeOf(context, confirmUri!!) } ?: 0L
        AlertDialog(
            onDismissRequest = { confirmUri = null },
            title = { Text(BackupRu.restoreTitle) },
            text = {
                Column {
                    Text(BackupRu.restoreBody, color = Forest)
                    if (info != null) {
                        Text(manifestLine(info), color = Amber, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${BackupRu.sizeLabel}: ${BackupManager.humanSize(sizeBytes)}",
                            color = Amber,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = confirmUri
                    confirmUri = null
                    if (uri != null) applyRestore(uri, restorePassword)
                }) { Text(BackupRu.restoreYes) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUri = null }) { Text(Ru.cancel) }
            }
        )
    }

    BackupServerSection(
        busy = busy,
        email = email,
        linked = linked,
        code = code,
        codeSent = codeSent,
        slot = slot,
        onEmailChange = { email = it },
        onCodeChange = { code = it },
        onCodeSent = { codeSent = true },
        notice = { report(it) },
        onLinked = {
            linked = true
            code = ""
            codeSent = false
        },
        onSlot = { slot = it },
        onLogout = {
            BackupServer.logout(context)
            BackupAutoWorker.cancel(context)
            linked = false
            slot = null
            codeSent = false
            report(null)
        },
        scopeBusy = { value ->
            busy = value
            if (value) progress = null
        }
    )
}

@Composable
private fun reportNotice(notice: String?) {
    if (!notice.isNullOrBlank()) {
        Text(notice, color = Amber, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    allowEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, color = Forest, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(BackupRu.passwordEnter) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.ifBlank { null }) }) { Text(BackupRu.passwordApply) }
        },
        dismissButton = {
            TextButton(onClick = { if (allowEmpty) onConfirm(null) else onDismiss() }) {
                Text(if (allowEmpty) BackupRu.passwordEmpty else Ru.cancel)
            }
        }
    )
}

private fun manifestLine(info: BackupManifest): String {
    val stamp = if (info.createdAt > 0L) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(info.createdAt))
    } else {
        "—"
    }
    return "$stamp · версия ${info.versionName} (${info.versionCode})"
}

private fun fileSizeOf(context: android.content.Context, uri: Uri): Long = runCatching {
    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
}.getOrDefault(0L)

@Composable
private fun BackupServerSection(
    busy: Boolean,
    email: String,
    linked: Boolean,
    code: String,
    codeSent: Boolean,
    slot: ServerSlot?,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onCodeSent: () -> Unit,
    notice: (String?) -> Unit,
    onLinked: () -> Unit,
    onSlot: (ServerSlot?) -> Unit,
    onLogout: () -> Unit,
    scopeBusy: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (linked) {
        LaunchedEffect(linked) {
            val answer = BackupServer.meta(context)
            if (answer.ok) onSlot(answer.value)
        }
    }

    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(BackupRu.serverTitle, color = Amber, style = MaterialTheme.typography.labelMedium)
        Text(
            BackupRu.serverHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        if (!linked) {
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text(BackupRu.serverEmail) },
                singleLine = true
            )
            if (codeSent) {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text(BackupRu.serverCode) },
                    singleLine = true
                )
            }
            JournalButton(
                label = if (codeSent) BackupRu.serverLogin else BackupRu.serverSendCode,
                filled = true,
                enabled = !busy,
                onClick = {
                    val target = email.trim()
                    scope.launch {
                        scopeBusy(true)
                        if (!codeSent) {
                            val answer = BackupServer.requestCode(context, target)
                            scopeBusy(false)
                            if (answer.ok) {
                                onCodeSent()
                                notice(BackupRu.serverCodeSent)
                            } else {
                                notice(serverMessage(answer.code))
                            }
                        } else {
                            val answer = BackupServer.login(context, target, code.trim(), BackupServer.deviceIdOf(context))
                            scopeBusy(false)
                            if (answer.ok) {
                                BackupAutoWorker.schedule(context)
                                onLinked()
                                notice(null)
                                val meta = BackupServer.meta(context)
                                if (meta.ok) onSlot(meta.value)
                            } else {
                                notice(serverMessage(answer.code))
                            }
                        }
                    }
                }
            )
        } else {
            Text(email, color = Forest, style = MaterialTheme.typography.bodySmall)
            val current = slot
            if (current == null) {
                Text(BackupRu.serverNoCopy, color = Amber, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "${BackupRu.serverLastPush}: ${stampOf(current.uploadedAt)} · " +
                        BackupManager.humanSize(current.size),
                    color = Amber,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            JournalButton(
                label = BackupRu.serverUpload,
                filled = true,
                enabled = !busy,
                onClick = {
                    scope.launch {
                        scopeBusy(true)
                        val archive = File(File(context.cacheDir, "backup_manual"), "manual.zip")
                        val exported = BackupManager.exportToFile(context, archive, null)
                        if (!exported.ok) {
                            scopeBusy(false)
                            notice(BackupRu.savedError)
                            return@launch
                        }
                        val answer = BackupServer.upload(context, archive)
                        archive.delete()
                        scopeBusy(false)
                        if (answer.ok) {
                            onSlot(answer.value)
                            notice(BackupRu.serverUploaded)
                        } else {
                            notice(serverMessage(answer.code))
                        }
                    }
                }
            )
            JournalButton(
                label = BackupRu.serverDownload,
                enabled = !busy,
                onClick = {
                    scope.launch {
                        scopeBusy(true)
                        val target = File(File(context.cacheDir, "backup_download"), "download.zip")
                        val answer = BackupServer.download(context, target)
                        if (!answer.ok) {
                            scopeBusy(false)
                            notice(serverMessage(answer.code))
                            return@launch
                        }
                        BackupManager.saveRollback(context)
                        val outcome = BackupManager.restoreFromFile(context, target, null)
                        target.delete()
                        scopeBusy(false)
                        if (outcome.ok) {
                            Toast.makeText(context, BackupRu.restoredOk, Toast.LENGTH_LONG).show()
                            BackupManager.restartApp(context)
                        } else {
                            notice(BackupRu.restoredError)
                        }
                    }
                }
            )
            JournalButton(label = BackupRu.serverLogout, enabled = !busy, onClick = onLogout)
        }
    }
}

private fun stampOf(value: String): String =
    if (value.isBlank()) "—" else value.take(16).replace('T', ' ')

private fun serverMessage(code: String): String = when (code) {
    "mail", "disabled" -> BackupRu.serverNotConfigured
    "code", "auth" -> BackupRu.serverWrongCode
    "email" -> BackupRu.serverBadEmail
    "too_large" -> BackupRu.serverTooLarge
    "cooldown", "empty" -> BackupRu.serverUploaded
    else -> BackupRu.serverError
}
