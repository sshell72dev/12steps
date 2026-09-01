package ru.na.step4.obidy.data.messenger

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MessengerVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): File {
        stop(delete = true)
        val out = File(context.cacheDir, "messenger_rec_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(32_000)
        rec.setAudioSamplingRate(16_000)
        rec.setMaxDuration(60_000)
        rec.setOutputFile(out.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        file = out
        startedAt = System.currentTimeMillis()
        return out
    }

    fun stop(delete: Boolean = false): Pair<File, Int>? {
        val rec = recorder
        val out = file
        recorder = null
        file = null
        val duration = (System.currentTimeMillis() - startedAt).toInt().coerceAtLeast(0)
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        if (out == null) return null
        if (delete || duration < 400 || !out.exists() || out.length() < 32) {
            out.delete()
            return null
        }
        return out to duration.coerceAtMost(60_000)
    }
}

class MessengerVoicePlayer {
    private var player: MediaPlayer? = null
    private val _playingId = MutableStateFlow<Long?>(null)
    val playingId: StateFlow<Long?> = _playingId.asStateFlow()

    fun toggle(id: Long, file: File) {
        if (_playingId.value == id) {
            stop()
            return
        }
        stop()
        val next = MediaPlayer()
        try {
            next.setDataSource(file.absolutePath)
            next.setOnCompletionListener { stop() }
            next.prepare()
            next.start()
            player = next
            _playingId.value = id
        } catch (_: Exception) {
            runCatching { next.release() }
            _playingId.value = null
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        _playingId.value = null
    }
}

fun formatVoiceDuration(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
