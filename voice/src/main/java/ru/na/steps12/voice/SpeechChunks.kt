package ru.na.steps12.voice

/**
 * Темп озвучки: сколько знаков текста читается за секунду при скорости 1×.
 * По нему считаем минуты прослушивания, ведь у TTS нет своей позиции.
 */
internal const val CHARS_PER_SECOND = 15.5

/**
 * Порция текста для одного вызова TTS. Держим её небольшой (примерно 30 секунд
 * чтения): пауза и перемотка продолжаются с начала порции, поэтому чем она короче,
 * тем точнее перемотка по минутам и меньше повтора при продолжении.
 */
internal const val SPEECH_CHUNK_CHARS = 500

/** Режет текст на порции по абзацам и предложениям, не разрывая слова. */
internal fun speechChunks(text: String, max: Int = SPEECH_CHUNK_CHARS): List<String> {
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

/** Оценка длительности порции в секундах: медленная озвучка тянется дольше. */
internal fun chunkSeconds(chunk: String, speed: Float): Double =
    chunk.length / (CHARS_PER_SECOND * speed.coerceIn(0.5f, 1.8f).toDouble())

/** Сколько секунд текста идёт до порции [index]. */
internal fun secondsBefore(chunks: List<String>, index: Int, speed: Float): Double {
    var passed = 0.0
    for (i in 0 until index.coerceIn(0, chunks.size)) passed += chunkSeconds(chunks[i], speed)
    return passed
}

/** Номер порции, в которой начинается секунда [seconds] от начала текста. */
internal fun chunkAtSeconds(chunks: List<String>, seconds: Double, speed: Float): Int {
    var passed = 0.0
    chunks.forEachIndexed { index, chunk ->
        if (passed + chunkSeconds(chunk, speed) > seconds) return index
        passed += chunkSeconds(chunk, speed)
    }
    return (chunks.size - 1).coerceAtLeast(0)
}

/** Номер порции, с которой начинается минута [minute] (1 — первая минута). */
internal fun chunkAtMinute(chunks: List<String>, minute: Int, speed: Float): Int =
    chunkAtSeconds(chunks, (minute.coerceAtLeast(1) - 1) * 60.0, speed)
