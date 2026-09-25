package ru.na.step4.obidy.data.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.data.AppDatabase
import ru.na.step4.obidy.data.messenger.MessengerDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/** Результат операции с копией: detail — код причины либо число записей. */
data class BackupOutcome(val ok: Boolean, val detail: String = "")

/** Ход операции: сколько записей записано и сколько байт уже прошло. */
data class BackupProgress(val records: Int, val bytes: Long, val totalBytes: Long)

/** Содержимое manifest.json — по нему проверяем, наша ли это копия. */
data class BackupManifest(
    val format: Int,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val createdAt: Long,
    val encrypted: Boolean
)

/**
 * Полная копия данных приложения одним файлом: базы Room, все SharedPreferences
 * и файлы внутренней памяти. Нужна для переноса на другой телефон и для
 * восстановления после переустановки приложения.
 */
object BackupManager {

    const val FORMAT = 1
    private const val MANIFEST = "manifest.json"
    private const val DIR_DATABASES = "databases"
    private const val DIR_PREFS = "shared_prefs"
    private const val DIR_FILES = "files"
    private const val BUFFER = 64 * 1024
    private const val ROLLBACK_DIR = "backup_rollback"

    private val DATABASES = listOf("step4_obidy.db", "messenger.db")

    /** Кэш переводов восстанавливается сам, в копию не попадает. */
    private val SKIPPED_DIRS = setOf("i18n")

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "12steps-backup-$stamp.zip"
    }

    /** Автокопия перед восстановлением: лежит в приватном кэше и в архивы не попадает. */
    fun rollbackFile(context: Context): File = File(File(context.cacheDir, ROLLBACK_DIR), "rollback.zip")

    fun rollbackAvailable(context: Context): Boolean = rollbackFile(context).let { it.isFile && it.length() > 0 }

    suspend fun export(
        context: Context,
        uri: Uri,
        password: CharArray?,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            checkpointDatabases(app)
            val total = estimateBytes(app)
            val stream = app.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupOutcome(false, "no stream")
            stream.use { raw ->
                val secret = password?.takeIf { it.isNotEmpty() }
                val target = if (secret == null) raw else BackupCrypto.encrypting(raw, secret)
                try {
                    writeArchive(app, target, total, onProgress)
                } finally {
                    if (target !== raw) target.close()
                }
            }
            BackupOutcome(true)
        } catch (t: Throwable) {
            BackupOutcome(false, t.message.orEmpty())
        }
    }

    /** Автокопия текущего состояния. Пароль не нужен: файл лежит в приватном каталоге приложения. */
    suspend fun saveRollback(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching {
            checkpointDatabases(app)
            val target = rollbackFile(app)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "rollback.part")
            temp.outputStream().buffered().use { out ->
                writeArchive(app, out, estimateBytes(app)) {}
            }
            if (target.exists()) target.delete()
            temp.renameTo(target)
        }.isSuccess
    }

    suspend fun restoreFromUri(
        context: Context,
        uri: Uri,
        password: CharArray?,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val total = contentLength(app, uri)
        runRestore(app, { app.contentResolver.openInputStream(uri) }, password, total, onProgress)
    }

    suspend fun restoreFromFile(
        context: Context,
        file: File,
        password: CharArray?,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runRestore(app, { file.inputStream() }, password, file.length(), onProgress)
    }

    /** Читает manifest.json, чтобы показать версию копии и проверить принадлежность приложению. */
    suspend fun readManifest(context: Context, uri: Uri, password: CharArray?): BackupManifest? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { raw ->
                    val encrypted = BackupCrypto.isEncrypted(raw)
                    if (encrypted && (password == null || password.isEmpty())) {
                        null
                    } else {
                        readManifestEntry(raw, password, encrypted)
                    }
                }
            }.getOrNull()
        }

    fun isEncrypted(context: Context, uri: Uri): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        return input.use { runCatching { BackupCrypto.isEncrypted(it) }.getOrDefault(false) }
    }

    /** Открытые базы уже не переживут подмену файлов, поэтому приложение перезапускается. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent == null) {
            exitProcess(0)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarm?.set(AlarmManager.RTC, System.currentTimeMillis() + 400L, pending)
        exitProcess(0)
    }

    private fun parseManifest(text: String, encrypted: Boolean): BackupManifest {
        val json = JSONObject(text)
        return BackupManifest(
            format = json.optInt("format", 0),
            packageName = json.optString("package"),
            versionName = json.optString("versionName"),
            versionCode = json.optInt("versionCode", 0),
            createdAt = json.optLong("createdAt", 0L),
            encrypted = encrypted
        )
    }

    private fun runRestore(
        context: Context,
        open: () -> InputStream?,
        password: CharArray?,
        totalBytes: Long,
        onProgress: (BackupProgress) -> Unit
    ): BackupOutcome {
        try {
            val probe = open() ?: return BackupOutcome(false, "no stream")
            val encrypted = probe.use { BackupCrypto.isEncrypted(it) }
            if (encrypted && (password == null || password.isEmpty())) return BackupOutcome(false, "password")

            val manifest = open()?.use { readManifestEntry(it, password, encrypted) }
                ?: return BackupOutcome(false, "manifest")
            if (manifest.format !in 1..FORMAT) return BackupOutcome(false, "format")
            if (manifest.packageName.isNotEmpty() && manifest.packageName != context.packageName) {
                return BackupOutcome(false, "package")
            }

            closeDatabases(context)
            clearData(context)
            val restored = open()?.use { input ->
                val source = if (encrypted) {
                    // Заголовок уже проверен выше, здесь только снимаем шифр.
                    BackupCrypto.isEncrypted(input)
                    BackupCrypto.decrypting(input, password!!)
                } else {
                    input
                }
                unpack(context, source, totalBytes, onProgress)
            } ?: 0
            if (restored <= 0) return BackupOutcome(false, "empty")
            return BackupOutcome(true, restored.toString())
        } catch (t: Throwable) {
            if (BackupCrypto.isAuthFailure(t)) return BackupOutcome(false, "password")
            return BackupOutcome(false, t.message.orEmpty())
        }
    }

    private fun readManifestEntry(
        input: InputStream,
        password: CharArray?,
        encrypted: Boolean
    ): BackupManifest? {
        val source = if (encrypted) {
            val secret = password?.takeIf { it.isNotEmpty() } ?: return null
            BackupCrypto.decrypting(input, secret)
        } else {
            input
        }
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == MANIFEST) {
                    return parseManifest(zip.readBytes().toString(Charsets.UTF_8), encrypted)
                }
                zip.closeEntry()
            }
        }
        return null
    }

    private class ProgressCounter(
        private val total: Long,
        private val onProgress: (BackupProgress) -> Unit
    ) {
        private var records = 0
        private var bytes = 0L

        fun bytes(count: Int) {
            bytes += count.toLong()
        }

        fun record() {
            records++
            onProgress(BackupProgress(records, bytes, total))
        }
    }

    private fun writeArchive(
        context: Context,
        out: OutputStream,
        totalBytes: Long,
        onProgress: (BackupProgress) -> Unit
    ) {
        val counter = ProgressCounter(totalBytes, onProgress)
        ZipOutputStream(out.buffered()).use { zip ->
            writeManifest(context, zip)
            writeDatabases(context, zip, counter)
            writePrefs(context, zip, counter)
            writeFiles(context, zip, counter)
        }
    }

    private fun writeManifest(context: Context, zip: ZipOutputStream) {
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("package", context.packageName)
            .put("versionName", BuildConfig.APP_VERSION_NAME)
            .put("versionCode", BuildConfig.APP_VERSION_CODE)
            .put("createdAt", System.currentTimeMillis())
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeDatabases(context: Context, zip: ZipOutputStream, counter: ProgressCounter) {
        DATABASES.forEach { name ->
            val base = context.getDatabasePath(name)
            listOf(base, File("${base.path}-wal"), File("${base.path}-shm")).forEach { file ->
                if (file.isFile) zipFile(zip, "$DIR_DATABASES/${file.name}", file, counter)
            }
        }
    }

    private fun writePrefs(context: Context, zip: ZipOutputStream, counter: ProgressCounter) {
        prefsDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".xml") }
            ?.forEach { zipFile(zip, "$DIR_PREFS/${it.name}", it, counter) }
    }

    private fun writeFiles(context: Context, zip: ZipOutputStream, counter: ProgressCounter) {
        walkFiles(context.filesDir, "", zip, counter, true)
    }

    private fun walkFiles(
        dir: File,
        prefix: String,
        zip: ZipOutputStream,
        counter: ProgressCounter,
        root: Boolean
    ) {
        dir.listFiles()?.forEach { file ->
            val path = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
            if (file.isDirectory) {
                if (root && file.name in SKIPPED_DIRS) return@forEach
                walkFiles(file, path, zip, counter, false)
            } else {
                zipFile(zip, "$DIR_FILES/$path", file, counter)
            }
        }
    }

    private fun zipFile(zip: ZipOutputStream, name: String, file: File, counter: ProgressCounter) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                zip.write(buffer, 0, read)
                counter.bytes(read)
            }
        }
        zip.closeEntry()
        counter.record()
    }

    private fun unpack(
        context: Context,
        source: InputStream,
        totalBytes: Long,
        onProgress: (BackupProgress) -> Unit
    ): Int {
        var records = 0
        var bytes = 0L
        val buffer = ByteArray(BUFFER)
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val target = targetFile(context, entry.name)
                    if (target != null) {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { out ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                bytes += read.toLong()
                            }
                        }
                        records++
                        onProgress(BackupProgress(records, bytes, totalBytes))
                    }
                }
                zip.closeEntry()
            }
        }
        return records
    }

    private fun targetFile(context: Context, entryName: String): File? = when {
        entryName.startsWith("$DIR_DATABASES/") ->
            context.getDatabasePath(File(entryName).name)
        entryName.startsWith("$DIR_PREFS/") ->
            File(prefsDir(context), File(entryName).name)
        entryName.startsWith("$DIR_FILES/") -> {
            val relative = entryName.removePrefix("$DIR_FILES/")
            if (relative.isBlank()) {
                null
            } else {
                val root = context.filesDir
                val file = File(root, relative)
                if (file.canonicalPath.startsWith(root.canonicalPath)) file else null
            }
        }
        else -> null
    }

    private fun closeDatabases(context: Context) {
        runCatching { AppDatabase.get(context).close() }
        runCatching { MessengerDatabase.get(context).close() }
    }

    private fun checkpointDatabases(context: Context) {
        DATABASES.forEach { name ->
            val file = context.getDatabasePath(name)
            if (!file.exists()) return@forEach
            runCatching {
                val db = SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                db.close()
            }
        }
    }

    private fun clearData(context: Context) {
        DATABASES.forEach { name ->
            val base = context.getDatabasePath(name)
            listOf(base, File("${base.path}-wal"), File("${base.path}-shm"), File("${base.path}-journal"))
                .forEach { if (it.exists()) it.delete() }
        }
        prefsDir(context).listFiles()?.forEach { if (it.isFile) it.delete() }
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun prefsDir(context: Context): File = File(context.applicationInfo.dataDir, DIR_PREFS)

    private fun estimateBytes(context: Context): Long {
        var total = 0L
        DATABASES.forEach { name ->
            val base = context.getDatabasePath(name)
            listOf(base, File("${base.path}-wal")).forEach { if (it.isFile) total += it.length() }
        }
        prefsDir(context).listFiles()?.forEach { if (it.isFile) total += it.length() }
        total += dirSize(context.filesDir, true)
        return total
    }

    private fun dirSize(dir: File, root: Boolean): Long {
        var total = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (root && file.name in SKIPPED_DIRS) return@forEach
                total += dirSize(file, false)
            } else {
                total += file.length()
            }
        }
        return total
    }

    private fun contentLength(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    }.getOrDefault(0L)

    /** Экспорт во внутренний файл: нужен для суточной выгрузки копии на сервер. */
    suspend fun exportToFile(
        context: Context,
        file: File,
        password: CharArray?,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            checkpointDatabases(app)
            val total = estimateBytes(app)
            file.parentFile?.mkdirs()
            file.outputStream().buffered().use { raw ->
                val secret = password?.takeIf { it.isNotEmpty() }
                val target = if (secret == null) raw else BackupCrypto.encrypting(raw, secret)
                try {
                    writeArchive(app, target, total, onProgress)
                } finally {
                    if (target !== raw) target.close()
                }
            }
            BackupOutcome(true)
        } catch (t: Throwable) {
            BackupOutcome(false, t.message.orEmpty())
        }
    }

    /** Размер для интерфейса: «3,2 МБ» или «740 КБ». */
    fun humanSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f МБ", mb)
        } else {
            String.format(Locale.US, "%d КБ", bytes / 1024)
        }
    }
}
