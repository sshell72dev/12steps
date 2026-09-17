package ru.na.step4.obidy.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Скачивание APK и запуск системной установки через FileProvider. */
object ApkUpdater {
    private const val AUTHORITY_SUFFIX = ".updates"

    fun targetFile(context: Context, info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        return File(dir, "12steps-${info.versionName}.apk")
    }

    fun isReady(context: Context, info: UpdateInfo): Boolean {
        val file = targetFile(context, info)
        return file.isFile && file.length() > 0L
    }

    /** onProgress получает 0..100 либо -1, если сервер не сообщил размер. */
    fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File? {
        val file = targetFile(context, info)
        val partial = File(file.parentFile, "${file.name}.part")
        partial.delete()
        val connection = try {
            (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = true
            }
        } catch (_: Exception) {
            return null
        }
        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(if (total > 0) ((written * 100) / total).toInt() else -1)
                        }
                    }
                }
                file.delete()
                if (partial.renameTo(file)) file else null
            }
        } catch (_: Exception) {
            partial.delete()
            null
        } finally {
            connection.disconnect()
        }
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Открывает системный экран «Установка неизвестных приложений» для нашего пакета. */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun install(context: Context, file: File): Boolean = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess
}
