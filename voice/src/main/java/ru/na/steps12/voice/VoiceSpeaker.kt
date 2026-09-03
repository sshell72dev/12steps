package ru.na.steps12.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceSpeaker(
    context: Context,
    private val store: VoiceSettingsStore
) {
    private val app = context.applicationContext
    private var engine: TextToSpeech? = null
    private var pending: String? = null
    private var pendingChunks = 0
    private var speakGen = 0
    private var focusRequest: AudioFocusRequest? = null

    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val wakeLock: PowerManager.WakeLock by lazy {
        (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "steps12:tts")
            .apply { setReferenceCounted(false) }
    }

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun ensureReady() {
        if (engine != null) return
        engine = TextToSpeech(app) { status ->
            val ok = status == TextToSpeech.SUCCESS
            _ready.value = ok
            if (ok) {
                applyVoice()
                pending?.let { text ->
                    pending = null
                    doSpeak(text)
                }
            }
        }.also { tts ->
            tts.setAudioAttributes(audioAttrs)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    markSpeaking(true)
                }
                override fun onDone(utteranceId: String?) {
                    onChunkFinished(utteranceId)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onChunkFinished(utteranceId)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    onChunkFinished(utteranceId)
                }
            })
        }
    }

    private fun onChunkFinished(utteranceId: String?) {
        val gen = utteranceId?.substringAfter("voice-", "")?.substringBefore("-")?.toIntOrNull()
        val left = synchronized(this) {
            if (gen != null && gen != speakGen) return
            pendingChunks = (pendingChunks - 1).coerceAtLeast(0)
            pendingChunks
        }
        if (left == 0) markSpeaking(false)
    }

    var speakingListener: ((Boolean, String) -> Unit)? = null
    private var lastPreview: String = ""

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        lastPreview = clean.replace('\n', ' ').take(120)
        ensureReady()
        if (!_ready.value) {
            pending = clean
            return
        }
        doSpeak(clean)
    }

    fun stop() {
        pending = null
        synchronized(this) {
            speakGen += 1
            pendingChunks = 0
        }
        engine?.stop()
        markSpeaking(false)
    }

    fun toggle(text: String) {
        if (_speaking.value) stop() else speak(text)
    }

    fun release() {
        stop()
        engine?.shutdown()
        engine = null
        _ready.value = false
    }

    fun deviceVoiceOptions(): List<VoiceOption> {
        val tts = engine ?: return emptyList()
        if (!_ready.value) return emptyList()
        tts.language = Locale.forLanguageTag(VoiceI18n.speechTag)
        return russianVoices(tts)
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
            .map { voice ->
                VoiceOption(
                    id = voice.name,
                    provider = "android",
                    label = "Телефон · ${friendlyDeviceName(voice)}",
                    gender = VoiceCatalog.inferGender(voice.name)
                )
            }
    }

    private fun doSpeak(text: String) {
        val tts = engine ?: return
        applyVoice()
        val spoken = stripForSpeech(text)
        if (spoken.isBlank()) return
        val limit = runCatching { TextToSpeech.getMaxSpeechInputLength() }
            .getOrDefault(4000)
            .coerceIn(800, 4000) - 80
        val chunks = chunkForSpeech(spoken, limit)
        if (chunks.isEmpty()) return
        val gen = synchronized(this) {
            speakGen += 1
            pendingChunks = chunks.size
            speakGen
        }
        markSpeaking(true)
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val code = tts.speak(chunk, mode, params, "voice-$gen-$index")
            if (code != TextToSpeech.SUCCESS) {
                synchronized(this) {
                    if (gen == speakGen) pendingChunks = 0
                }
                markSpeaking(false)
                return
            }
        }
    }

    private fun markSpeaking(on: Boolean) {
        val was = _speaking.value
        if (was == on) return
        _speaking.value = on
        if (on) holdPlayback() else releasePlayback()
        speakingListener?.invoke(on, lastPreview)
    }

    private fun holdPlayback() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttrs)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        runCatching { am.requestAudioFocus(request) }
        runCatching {
            if (!wakeLock.isHeld) wakeLock.acquire(30 * 60 * 1000L)
        }
    }

    private fun releasePlayback() {
        runCatching { if (wakeLock.isHeld) wakeLock.release() }
        val request = focusRequest ?: return
        focusRequest = null
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { am.abandonAudioFocusRequest(request) }
    }

    private fun stripForSpeech(text: String): String =
        text.replace(Regex("#{1,6}\\s*"), "")
            .replace("**", "")
            .replace("*", "")
            .replace(Regex("[ \\t]+"), " ")
            .trim()

    private fun chunkForSpeech(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val parts = mutableListOf<String>()
        val buf = StringBuilder()
        fun flush() {
            val piece = buf.toString().trim()
            if (piece.isNotEmpty()) parts += piece
            buf.clear()
        }
        text.split(Regex("\\n+")).forEach { paragraph ->
            if (paragraph.length > max) {
                flush()
                var rest = paragraph
                while (rest.length > max) {
                    var cut = rest.lastIndexOf(' ', max)
                    if (cut < max / 3) cut = max
                    parts += rest.substring(0, cut).trim()
                    rest = rest.substring(cut).trim()
                }
                if (rest.isNotBlank()) buf.append(rest)
            } else if (buf.length + paragraph.length + 1 > max) {
                flush()
                buf.append(paragraph)
            } else {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(paragraph)
            }
        }
        flush()
        return parts.ifEmpty { listOf(text.take(max)) }
    }

    private fun applyVoice() {
        val tts = engine ?: return
        if (!_ready.value) return
        val cfg = store.snapshot
        val option = cfg.option
        tts.setAudioAttributes(audioAttrs)
        tts.language = Locale.forLanguageTag(VoiceI18n.speechTag)
        tts.setSpeechRate(cfg.speed.coerceIn(0.5f, 1.8f))
        val chosen = pickVoice(tts, option)
        if (chosen != null) {
            tts.voice = chosen
        }
        tts.setPitch(pitchFor(tts, option, chosen))
    }

    private fun pickVoice(tts: TextToSpeech, option: VoiceOption): Voice? {
        val ru = russianVoices(tts)
        if (ru.isEmpty()) return null
        if (option.provider == "android") {
            ru.firstOrNull { it.name == option.id }?.let { return it }
        }
        val wantFemale = option.gender != "male"
        val scored = ru.map { voice ->
            voice to score(voice, wantFemale)
        }.sortedWith(
            compareByDescending<Pair<Voice, Int>> { it.second }
                .thenByDescending { it.first.quality }
                .thenBy { it.first.isNetworkConnectionRequired }
        )
        val best = scored.firstOrNull() ?: return null
        if (best.second <= 0 && ru.size == 1) return ru.first()
        return best.first
    }

    private fun score(voice: Voice, wantFemale: Boolean): Int {
        val gender = VoiceCatalog.inferGender(voice.name)
        var points = 0
        when {
            wantFemale && gender == "female" -> points += 40
            !wantFemale && gender == "male" -> points += 40
            gender == "unknown" -> points += 1
            else -> points -= 20
        }
        if (!voice.isNetworkConnectionRequired) points += 4
        if ("notInstalled" in voice.features) points -= 50
        return points
    }

    private fun pitchFor(tts: TextToSpeech, option: VoiceOption, chosen: Voice?): Float {
        if (option.provider == "android") return 1f
        val distinct = russianVoices(tts)
            .map { VoiceCatalog.inferGender(it.name) }
            .filter { it != "unknown" }
            .distinct()
            .size >= 2
        if (distinct && chosen != null && VoiceCatalog.inferGender(chosen.name) != "unknown") {
            return 1f
        }
        return if (option.gender == "male") 0.82f else 1.14f
    }

    private fun russianVoices(tts: TextToSpeech): List<Voice> {
        val all = tts.voices ?: return emptyList()
        val lang = Locale.forLanguageTag(VoiceI18n.speechTag).language.ifBlank { "ru" }
        val matched = all.filter { voice ->
            voice.locale.language.equals(lang, ignoreCase = true) &&
                "notInstalled" !in voice.features
        }
        if (matched.isNotEmpty()) return matched
        return all.filter { "notInstalled" !in it.features }
    }

    private fun friendlyDeviceName(voice: Voice): String {
        val gender = when (VoiceCatalog.inferGender(voice.name)) {
            "male" -> "муж."
            "female" -> "жен."
            else -> voice.locale.toLanguageTag()
        }
        val short = voice.name
            .substringAfterLast('-')
            .ifBlank { voice.name }
        val net = if (voice.isNetworkConnectionRequired) "сеть" else "офлайн"
        return "$short · $gender · $net"
    }
}
