package ru.na.step4.obidy.data.messenger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/** Вложения чата на стороне устройства: выбор файла, его имя и открытие системой. */
object MessengerAttachments {
    /** Лимит совпадает с серверным: больше сервер не примет. */
    const val MAX_BYTES = 10 * 1024 * 1024L

    private const val AUTHORITY_SUFFIX = ".updates"

    /** Имя выбранного файла: его показывает карточка в ленте. */
    fun displayName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "file" }
        val cursor = runCatching {
            context.contentResolver.query(uri, null, null, null, null)
        }.getOrNull() ?: return fallback
        cursor.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0 || !it.moveToFirst()) return fallback
            return it.getString(index)?.takeIf { name -> name.isNotBlank() } ?: fallback
        }
    }

    fun mimeType(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream"

    /**
     * Копия выбранного файла в кэш: сервер принимает байты, а исходный Uri
     * живёт только пока открыт выбор файла.
     */
    fun copyToCache(context: Context, uri: Uri, name: String): File? {
        val dir = File(context.cacheDir, "messenger_upload").apply { mkdirs() }
        val ext = name.substringAfterLast('.', "").take(8)
        val stamp = System.currentTimeMillis()
        val file = File(dir, if (ext.isBlank()) "attach_$stamp" else "attach_$stamp.$ext")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file.takeIf { it.length() > 0L }
        }.getOrNull()
    }

    /** Открывает файл системным приложением: напрямую file:// отдавать нельзя. */
    fun open(context: Context, file: File, mimeType: String): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}$AUTHORITY_SUFFIX",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.isSuccess
    }
}
