package com.example.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Creates the TTS engine on demand and shuts it down as soon as the phrase is spoken. */
class TtsManager(context: Context) {
    private val ctx = context.applicationContext
    private var tts: TextToSpeech? = null

    fun speak(text: String) {
        shutdown()
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { status ->
            val e = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { e.shutdown(); return@TextToSpeech }
            e.language = Locale("ru", "RU")
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { release(e) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { release(e) }
            })
            e.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "u1")
        }
        tts = engine
    }

    private fun release(e: TextToSpeech) {
        e.shutdown()
        if (tts === e) tts = null
    }

    fun shutdown() {
        tts?.let { it.stop(); it.shutdown() }
        tts = null
    }
}
