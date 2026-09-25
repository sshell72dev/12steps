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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

data class BackupOutcome(val ok: Boolean, val detail: String = "")

/**
 * Полная копия данных приложения одним файлом: базы Room, все SharedPreferences
 * и файлы внутренней памяти. Нужна для переноса на другой телефон и для
 * восстановления после переустановки приложения.
 */
object BackupManager {

    private const val FORMAT = 1
    private const val MANIFEST = "manifest.json"
    private const val DIR_DATABASES = "databases"
    private const val DIR_PREFS = "shared_prefs"
    private const val DIR_FILES = "files"
    private const val BUFFER = 64 * 1024

    private val DATABASES = listOf("step4_obidy.db", "messenger.db")

    /** Кэш переводов восстанавливается сам, в копию не попадает. */
    private val SKIPPED_DIRS = setOf("i18n")

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "12steps-backup-$stamp.zip"
    }

    suspend fun export(context: Context, uri: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            checkpointDatabases(app)
            val stream = app.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupOutcome(false, "no stream")
            stream.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    writeManifest(app, zip)
                    writeDatabases(app, zip)
                    writePrefs(app, zip)
                    writeFiles(app, zip)
                }
            }
            BackupOutcome(true)
        } catch (t: Throwable) {
            BackupOutcome(false, t.message.orEmpty())
        }
    }

    suspend fun restore(context: Context, uri: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            if (!hasManifest(app, uri)) return@withContext BackupOutcome(false, "manifest")
            closeDatabases(app)
            clearData(app)
            val restored = unpack(app, uri)
            if (restored == 0) return@withContext BackupOutcome(false, "empty")
            BackupOutcome(true, restored.toString())
        } catch (t: Throwable) {
            BackupOutcome(false, t.message.orEmpty())
        }
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

    private fun hasManifest(context: Context, uri: Uri): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        return input.use { openZip(it) { zip -> zip.hasNext(MANIFEST) } } ?: false
    }

    private fun unpack(context: Context, uri: Uri): Int {
        val input = context.contentResolver.openInputStream(uri) ?: return 0
        var count = 0
        input.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                val buffer = ByteArray(BUFFER)
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
                                }
                            }
                            count++
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        return count
    }

    private fun targetFile(context: Context, entryName: String): File? {
        return when {
            entryName.startsWith("$DIR_DATABASES/") ->
                context.getDatabasePath(File(entryName).name)
            entryName.startsWith("$DIR_PREFS/") ->
                File(prefsDir(context), File(entryName).name)
            entryName.startsWith("$DIR_FILES/") -> {
                val relative = entryName.removePrefix("$DIR_FILES/")
                if (relative.isBlank()) return null
                val root = context.filesDir
                val file = File(root, relative)
                if (file.canonicalPath.startsWith(root.canonicalPath)) file else null
            }
            else -> null
        }
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

    private fun prefsDir(context: Context): File =
        File(context.applicationInfo.dataDir, DIR_PREFS)

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

    private fun writeDatabases(context: Context, zip: ZipOutputStream) {
        DATABASES.forEach { name ->
            val base = context.getDatabasePath(name)
            listOf(base, File("${base.path}-wal"), File("${base.path}-shm")).forEach { file ->
                if (file.isFile) zipFile(zip, "$DIR_DATABASES/${file.name}", file)
            }
        }
    }

    private fun writePrefs(context: Context, zip: ZipOutputStream) {
        prefsDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".xml") }
            ?.forEach { zipFile(zip, "$DIR_PREFS/${it.name}", it) }
    }

    private fun writeFiles(context: Context, zip: ZipOutputStream) {
        walkFiles(context.filesDir, "", zip)
    }

    private fun walkFiles(dir: File, prefix: String, zip: ZipOutputStream) {
        dir.listFiles()?.forEach { file ->
            val path = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
            if (file.isDirectory) {
                if (prefix.isEmpty() && file.name in SKIPPED_DIRS) return@forEach
                walkFiles(file, path, zip)
            } else {
                zipFile(zip, "$DIR_FILES/$path", file)
            }
        }
    }

    private fun zipFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun openZip(input: InputStream, block: (ZipInputStream) -> Boolean): Boolean? {
        ZipInputStream(input.buffered()).use { zip ->
            return block(zip)
        }
    }

    private fun ZipInputStream.hasNext(name: String): Boolean {
        while (true) {
            val entry = nextEntry ?: return false
            if (entry.name == name) return true
        }
    }
}
