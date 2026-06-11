package eu.kanade.tachiyomi.ui.novel

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Thin wrapper around Android Text-to-Speech for reading a novel chapter aloud. The chapter text is
 * split into engine-sized chunks and queued; [onDone] fires when the last chunk finishes.
 */
class NovelTts(context: Context, private val onDone: () -> Unit) {

    private var ready = false
    private var lastUtteranceId: String = ""

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) onDone()
            }
        })
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.stop()
        val maxLen = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(1000)
        val chunks = chunk(text, maxLen)
        lastUtteranceId = "novel_${chunks.lastIndex}"
        chunks.forEachIndexed { i, part ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(part, mode, null, "novel_$i")
        }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun chunk(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?\\n])"))) {
            if (sb.length + sentence.length > maxLen) {
                if (sb.isNotEmpty()) result.add(sb.toString())
                sb.clear()
                if (sentence.length > maxLen) {
                    sentence.chunked(maxLen).forEach { result.add(it) }
                } else {
                    sb.append(sentence)
                }
            } else {
                sb.append(sentence)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }
}
