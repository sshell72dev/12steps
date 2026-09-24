package ru.na.step4.obidy.data.messenger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Готовит выбранное в галерее фото к отправке: уменьшает сторону и сжимает в JPEG,
 * чтобы уложиться в лимит загрузки сервера.
 */
object MessengerImage {
    const val mimeType = "image/jpeg"

    private const val MAX_SIDE = 720
    private const val QUALITY = 85

    fun prepare(context: Context, uri: Uri): File? {
        val decoded = runCatching { decodeScaled(context, uri) }.getOrNull() ?: return null
        return runCatching {
            val dir = File(context.cacheDir, "messenger_upload").apply { mkdirs() }
            val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            decoded.recycle()
            if (file.length() == 0L) null else file
        }.getOrNull()
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (width / sample > MAX_SIDE * 2 || height / sample > MAX_SIDE * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}
