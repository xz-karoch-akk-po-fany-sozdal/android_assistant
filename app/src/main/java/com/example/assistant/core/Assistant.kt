package com.example.assistant.core

import com.example.assistant.App
import com.example.assistant.qa.OptionalCloudLlm
import com.example.assistant.voice.VoskRecognizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates heavy components so that only one is in memory at a time:
 * Vosk (listening) -> closed -> translator/OCR (each created and closed per call).
 */
class Assistant(private val app: App) {
    private val heavy = Mutex()
    private var vosk: VoskRecognizer? = null
    @Volatile private var jobs = 0

    val busy: Boolean get() = jobs > 0 || vosk?.isListening == true

    interface Listener {
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(message: String)
    }

    suspend fun startListening(l: Listener) {
        stopListening()
        val v = VoskRecognizer(app)
        vosk = v
        var delivered = false
        v.start(object : VoskRecognizer.Callback {
            override fun onPartial(text: String) = l.onPartial(text)
            override fun onFinal(text: String) {
                if (delivered) return
                delivered = true
                // first complete phrase is the command: release the model right away
                if (vosk === v) vosk = null
                v.stop()
                l.onResult(text)
            }
            override fun onError(message: String) {
                if (delivered) return
                delivered = true
                if (vosk === v) vosk = null
                v.stop()
                l.onError(message)
            }
        })
    }

    fun stopListening() {
        vosk?.stop()
        vosk = null
    }

    /** Local engine first; cloud only if a key is set and local did not understand. */
    suspend fun answer(question: String): String {
        jobs++
        try {
            val local = app.intents.handle(question)
            val text = if (local.handled) local.answer else {
                val key = app.secrets.getApiKey()
                if (key.isBlank()) local.answer
                else OptionalCloudLlm(key, app.settings.cloudBaseUrl, app.settings.cloudModel).ask(question)
            }
            app.history.add(question, text)
            if (app.settings.speak) app.tts.speak(text)
            return text
        } finally { jobs-- }
    }

    suspend fun translate(text: String): String {
        if (text.isBlank()) return "Нет текста для перевода."
        jobs++
        try {
            return heavy.withLock {
                stopListening()
                try {
                    app.translation.translateAuto(text, app.settings.foreignLang)
                        ?: "Языковые модели не скачаны. Скачайте их в настройках."
                } catch (e: Exception) {
                    "Ошибка перевода: ${e.message ?: "неизвестная"}"
                }
            }
        } finally { jobs-- }
    }

    suspend fun readScreenText(service: android.accessibilityservice.AccessibilityService): String {
        jobs++
        try {
            return heavy.withLock { stopListening(); app.ocr.readScreen(service) }
        } finally { jobs-- }
    }
}
